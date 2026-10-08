package org.connecttag.lib.interactions.compose

import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import org.connecttag.lib.location.manager.ForegroundLocationPurpose
import org.connecttag.lib.location.manager.LocationAccuracyRequirement

@Composable
internal actual fun rememberForegroundLocationSettingsLauncher(
    onResult: (LocationSettingsResolutionResult) -> Unit,
): ForegroundLocationSettingsLauncher {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    val fallbackSettings = rememberPlatformSettingsLauncher {
        currentOnResult.value(LocationSettingsResolutionResult.ReturnedFromSettings)
    }
    val resolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        currentOnResult.value(
            if (result.resultCode == Activity.RESULT_OK) {
                LocationSettingsResolutionResult.Changed
            } else {
                LocationSettingsResolutionResult.Declined
            },
        )
    }
    val settingsClient = remember(context) {
        LocationServices.getSettingsClient(context)
    }

    return remember(context, settingsClient, resolutionLauncher, fallbackSettings) {
        ForegroundLocationSettingsLauncher { purpose ->
            val request = purpose.toLocationSettingsRequest()
            settingsClient.checkLocationSettings(request)
                .addOnSuccessListener {
                    currentOnResult.value(LocationSettingsResolutionResult.Satisfied)
                }
                .addOnFailureListener { error ->
                    val resolution = error as? ResolvableApiException
                    if (resolution == null) {
                        fallbackSettings.launch(PlatformSettingsDestination.LocationServices)
                    } else {
                        try {
                            resolutionLauncher.launch(
                                IntentSenderRequest.Builder(resolution.resolution).build(),
                            )
                        } catch (_: IntentSender.SendIntentException) {
                            fallbackSettings.launch(PlatformSettingsDestination.LocationServices)
                        }
                    }
                }
        }
    }
}

private fun ForegroundLocationPurpose.toLocationSettingsRequest(): LocationSettingsRequest {
    val priority = when (accuracyRequirement) {
        LocationAccuracyRequirement.Approximate -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        LocationAccuracyRequirement.Precise -> Priority.PRIORITY_HIGH_ACCURACY
    }
    val intervalMillis = when (this) {
        ForegroundLocationPurpose.LiveTracking -> 10_000L
        ForegroundLocationPurpose.MapRecenter,
        ForegroundLocationPurpose.CurrentPosition -> 30_000L
    }
    val request = LocationRequest.Builder(priority, intervalMillis).build()
    return LocationSettingsRequest.Builder()
        .addLocationRequest(request)
        .setAlwaysShow(false)
        .build()
}
