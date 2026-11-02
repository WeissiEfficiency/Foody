// JNI-Hülle der Fotoerkennung (ADR 0017): Qwen3-VL über llama.cpp/mtmd, nur CPU. Gegenstück: LlamaNativ.kt.
// Eine Sitzung lädt Modell und Bild einmal; jede Frage setzt hinter dem Bild neu an, das Bild wird nicht neu gerechnet.

#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

namespace {

constexpr const char * TAG = "FoodyErkennung";
constexpr int KONTEXT = 4096;
constexpr int STAPEL = 512;

struct Sitzung {
    std::atomic<bool> abgebrochen{false};
    llama_model * modell = nullptr;
    llama_context * kontext = nullptr;
    mtmd_context * bildteil = nullptr;
    llama_sampler * auswahl = nullptr;
    // Position hinter Kopf und Bild: hierher springt jede Frage zurück
    llama_pos nachBild = 0;

    ~Sitzung() {
        if (auswahl) llama_sampler_free(auswahl);
        if (bildteil) mtmd_free(bildteil);
        if (kontext) llama_free(kontext);
        if (modell) llama_model_free(modell);
    }
};

Sitzung & sitzung(jlong h) { return *reinterpret_cast<Sitzung *>(h); }

// Nur Warnungen und Fehler ins Logcat, die Ladeausgabe von llama.cpp ist sehr gesprächig
void protokoll(ggml_log_level stufe, const char * text, void *) {
    if (stufe == GGML_LOG_LEVEL_ERROR) __android_log_write(ANDROID_LOG_ERROR, TAG, text);
    else if (stufe == GGML_LOG_LEVEL_WARN) __android_log_write(ANDROID_LOG_WARN, TAG, text);
}

bool abbrechen(void * daten) { return static_cast<Sitzung *>(daten)->abgebrochen.load(); }

bool weiterLaden(float, void * daten) { return !static_cast<Sitzung *>(daten)->abgebrochen.load(); }

std::string text(JNIEnv * env, jstring s) {
    const char * c = env->GetStringUTFChars(s, nullptr);
    std::string ergebnis(c);
    env->ReleaseStringUTFChars(s, c);
    return ergebnis;
}

// Text-Token ab Position [ab] auswerten, in Stapeln; nur für das letzte Token werden Logits gebraucht
bool auswerten(Sitzung & s, const std::vector<llama_token> & token, llama_pos ab) {
    llama_batch stapel = llama_batch_init(STAPEL, 0, 1);
    bool ok = true;
    for (size_t i = 0; ok && i < token.size(); i += STAPEL) {
        const size_t n = std::min<size_t>(STAPEL, token.size() - i);
        stapel.n_tokens = static_cast<int32_t>(n);
        for (size_t j = 0; j < n; j++) {
            stapel.token[j] = token[i + j];
            stapel.pos[j] = ab + static_cast<llama_pos>(i + j);
            stapel.n_seq_id[j] = 1;
            stapel.seq_id[j][0] = 0;
            stapel.logits[j] = (i + j == token.size() - 1);
        }
        ok = llama_decode(s.kontext, stapel) == 0;
    }
    llama_batch_free(stapel);
    return ok;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_de_foody_core_erkennung_LlamaNativ_neu(JNIEnv *, jobject) {
    static std::once_flag einmal;
    std::call_once(einmal, [] {
        llama_log_set(protokoll, nullptr);
        mtmd_helper_log_set(protokoll, nullptr);
        llama_backend_init();
    });
    return reinterpret_cast<jlong>(new Sitzung());
}

JNIEXPORT jboolean JNICALL Java_de_foody_core_erkennung_LlamaNativ_laden(
        JNIEnv * env, jobject, jlong h, jstring modellPfad, jstring bildteilPfad, jstring bildPfad, jint threads,
        jint maxBildTokens) {
    Sitzung & s = sitzung(h);

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    mp.progress_callback = weiterLaden;
    mp.progress_callback_user_data = &s;
    s.modell = llama_model_load_from_file(text(env, modellPfad).c_str(), mp);
    if (!s.modell) return JNI_FALSE;

    llama_context_params kp = llama_context_default_params();
    kp.n_ctx = KONTEXT;
    kp.n_batch = STAPEL;
    kp.n_ubatch = STAPEL;
    kp.n_threads = threads;
    kp.n_threads_batch = threads;
    kp.abort_callback = abbrechen;
    kp.abort_callback_data = &s;
    s.kontext = llama_init_from_model(s.modell, kp);
    if (!s.kontext || s.abgebrochen) return JNI_FALSE;

    mtmd_context_params bp = mtmd_context_params_default();
    bp.use_gpu = false;
    bp.n_threads = threads;
    bp.print_timings = false;
    bp.warmup = false;
    bp.image_max_tokens = maxBildTokens;
    bp.progress_callback = weiterLaden;
    bp.progress_callback_user_data = &s;
    s.bildteil = mtmd_init_from_file(text(env, bildteilPfad).c_str(), s.modell, bp);
    if (!s.bildteil || s.abgebrochen) return JNI_FALSE;

    s.auswahl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(s.auswahl, llama_sampler_init_greedy());

    mtmd_helper_bitmap_wrapper bild =
            mtmd_helper_bitmap_init_from_file(s.bildteil, text(env, bildPfad).c_str(), false, mtmd_helper_init_opt_default());
    if (bild.video_ctx) mtmd_helper_video_free(bild.video_ctx);
    if (!bild.bitmap) return JNI_FALSE;

    // Chat-Format von Qwen; mtmd setzt <|vision_start|> … <|vision_end|> um das Bild
    const std::string kopf = std::string("<|im_start|>user\n") + mtmd_default_marker();
    mtmd_input_text eingabe{kopf.c_str(), kopf.size(), /* add_special */ true, /* parse_special */ true};
    mtmd_input_chunks * teile = mtmd_input_chunks_init();
    const mtmd_bitmap * bilder[] = {bild.bitmap};
    bool ok = mtmd_tokenize(s.bildteil, teile, &eingabe, bilder, 1) == 0;
    llama_pos weiter = 0;
    ok = ok && !s.abgebrochen &&
         mtmd_helper_eval_chunks(s.bildteil, s.kontext, teile, 0, 0, STAPEL, false, &weiter) == 0;
    mtmd_input_chunks_free(teile);
    mtmd_bitmap_free(bild.bitmap);
    s.nachBild = weiter;
    return ok && !s.abgebrochen ? JNI_TRUE : JNI_FALSE;
}

// Antwort als UTF-8-Bytes: NewStringUTF erwartet Modified UTF-8 und verträgt z. B. keine Emoji
JNIEXPORT jbyteArray JNICALL Java_de_foody_core_erkennung_LlamaNativ_fragen(
        JNIEnv * env, jobject, jlong h, jstring anweisung, jint maxTokens) {
    Sitzung & s = sitzung(h);
    if (s.abgebrochen || !s.kontext) return nullptr;
    llama_memory_seq_rm(llama_get_memory(s.kontext), 0, s.nachBild, -1);
    llama_sampler_reset(s.auswahl);

    const llama_vocab * vokabular = llama_model_get_vocab(s.modell);
    const std::string rest = text(env, anweisung) + "<|im_end|>\n<|im_start|>assistant\n";
    const int n = -llama_tokenize(vokabular, rest.c_str(), static_cast<int32_t>(rest.size()), nullptr, 0, false, true);
    std::vector<llama_token> token(n);
    llama_tokenize(vokabular, rest.c_str(), static_cast<int32_t>(rest.size()), token.data(), n, false, true);
    if (!auswerten(s, token, s.nachBild)) return nullptr;

    llama_pos pos = s.nachBild + n;
    std::string antwort;
    for (int i = 0; i < maxTokens && !s.abgebrochen; i++) {
        llama_token t = llama_sampler_sample(s.auswahl, s.kontext, -1);
        if (llama_vocab_is_eog(vokabular, t)) break;
        char stueck[256];
        const int laenge = llama_token_to_piece(vokabular, t, stueck, sizeof stueck, 0, false);
        if (laenge > 0) antwort.append(stueck, laenge);
        if (!auswerten(s, {t}, pos++)) return nullptr;
    }
    if (s.abgebrochen) return nullptr;
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(antwort.size()));
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(antwort.size()), reinterpret_cast<const jbyte *>(antwort.data()));
    return bytes;
}

JNIEXPORT void JNICALL Java_de_foody_core_erkennung_LlamaNativ_abbrechen(JNIEnv *, jobject, jlong h) {
    sitzung(h).abgebrochen = true;
}

JNIEXPORT void JNICALL Java_de_foody_core_erkennung_LlamaNativ_freigeben(JNIEnv *, jobject, jlong h) {
    delete &sitzung(h);
}

}  // extern "C"
