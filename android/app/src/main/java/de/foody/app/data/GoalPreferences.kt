package de.foody.app.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Optionales Tagesziel in kcal (pro Person) für den Planer; null = kein Ziel. */
@Singleton
class GoalPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("foody", Context.MODE_PRIVATE)

    private val _dailyKcal = MutableStateFlow(prefs.getInt(KEY, 0).takeIf { it > 0 })
    val dailyKcal = _dailyKcal.asStateFlow()

    fun setDailyKcal(kcal: Int?) {
        val value = kcal?.takeIf { it in RANGE }
        prefs.edit { if (value == null) remove(KEY) else putInt(KEY, value) }
        _dailyKcal.value = value
    }

    companion object {
        private const val KEY = "dailyKcalGoal"
        /** Plausible Tagesziele; außerhalb gilt die Eingabe als „kein Ziel“. */
        val RANGE = 800..6000
    }
}
