package de.foody.app.ui.sync

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.foody.app.R
import de.foody.app.ui.common.ScreenHeader

/** Platzhalter für den Bildschirm "Server verbinden"; wird vom Verbinden-Assistenten (Task 3) ersetzt. */
@Composable
fun SyncSetupPlaceholder(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column {
        ScreenHeader(stringResource(R.string.sync_title), stringResource(R.string.sync_setup_title))
    }
}
