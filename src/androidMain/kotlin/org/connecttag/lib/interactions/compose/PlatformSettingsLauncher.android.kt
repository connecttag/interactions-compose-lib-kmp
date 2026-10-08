package org.connecttag.lib.interactions.compose

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberPlatformSettingsLauncher(
    onReturned: (PlatformSettingsDestination) -> Unit,
): PlatformSettingsLauncher {
    val context = LocalContext.current
    val currentOnReturned = rememberUpdatedState(onReturned)
    var pendingDestination by remember {
        mutableStateOf(PlatformSettingsDestination.LocationServices)
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        currentOnReturned.value(pendingDestination)
    }

    return remember(context, launcher) {
        PlatformSettingsLauncher { destination ->
            pendingDestination = destination
            try {
                launcher.launch(destination.toIntent(context))
            } catch (_: ActivityNotFoundException) {
                currentOnReturned.value(destination)
            }
        }
    }
}

private fun PlatformSettingsDestination.toIntent(context: Context): Intent {
    return when (this) {
        PlatformSettingsDestination.ApplicationDetails -> {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            )
        }
        PlatformSettingsDestination.LocationServices -> {
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        }
    }
}
