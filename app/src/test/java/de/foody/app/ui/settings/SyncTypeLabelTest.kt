package de.foody.app.ui.settings

import de.foody.app.R
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncTypeLabelTest {
    @Test fun tagebuchHatEigeneBezeichnung() =
        assertEquals(R.string.sync_type_tagebuch, SyncSettingsViewModel.typeLabel("tagebuch_eintrag"))
}
