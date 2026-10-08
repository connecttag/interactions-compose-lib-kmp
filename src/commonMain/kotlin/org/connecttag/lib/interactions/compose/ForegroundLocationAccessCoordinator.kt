package org.connecttag.lib.interactions.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import org.connecttag.lib.location.manager.ForegroundLocationPurpose
import org.connecttag.lib.location.manager.LocationAccuracyRequirement
import org.connecttag.lib.location.manager.LocationReadiness
import org.connecttag.lib.location.model.LocationError
import org.connecttag.lib.permissions.PermissionRequestResult
import org.connecttag.lib.permissions.PlatformPermissionStatus
import org.connecttag.lib.permissions.PlatformPermissionUnavailableReason

enum class ForegroundLocationPrompt {
    PermissionRequired,
    PrecisePermissionRequired,
    PermissionDenied,
    PermissionPermanentlyDenied,
    LocationServiceDisabled,
    LocationUnavailable,
}

enum class ForegroundLocationProgress {
    Idle,
    RequestingPermission,
    ResolvingSettings,
    Executing,
}

enum class ForegroundLocationDiagnosticStage {
    RequestReceived,
    ConcurrentRequestIgnored,
    PermissionPromptShown,
    PrecisePermissionPromptShown,
    PermissionRequestStarted,
    PermissionGranted,
    PermissionDenied,
    PermissionPermanentlyDenied,
    PermissionUnavailable,
    LocationServicePromptShown,
    LocationResolutionStarted,
    LocationSettingsChanged,
    LocationSettingsDeclined,
    LocationSettingsUnavailable,
    ActionStarted,
    ActionCompleted,
    ActionFailedPermission,
    ActionFailedService,
    ActionFailedUnsupported,
    ActionFailedTimeout,
    ActionFailedUnknown,
    Cancelled,
    Dismissed,
}

/** Contains only purpose and state transitions; never coordinates or provider messages. */
data class ForegroundLocationDiagnosticEvent(
    val purpose: ForegroundLocationPurpose,
    val stage: ForegroundLocationDiagnosticStage,
    val unavailableReason: PlatformPermissionUnavailableReason? = null,
)

@Stable
class ForegroundLocationAccessCoordinator internal constructor(
    private val state: ForegroundLocationAccessState,
    private val readiness: (LocationAccuracyRequirement) -> LocationReadiness,
    private val permissionLauncher: LocationPermissionLauncher,
    private val appSettingsLauncher: PlatformSettingsLauncher,
    private val locationSettingsLauncher: ForegroundLocationSettingsLauncher,
    private val onPermissionDenied: () -> Unit,
) {
    val prompt: ForegroundLocationPrompt?
        get() = state.prompt

    val progress: ForegroundLocationProgress
        get() = state.progress

    val isBusy: Boolean
        get() = progress != ForegroundLocationProgress.Idle

    val isExecuting: Boolean
        get() = progress == ForegroundLocationProgress.Executing

    val purpose: ForegroundLocationPurpose?
        get() = state.pendingPurpose

    fun request(
        purpose: ForegroundLocationPurpose,
        action: () -> Unit,
    ): Boolean {
        return state.request(
            purpose = purpose,
            readiness = readiness(purpose.accuracyRequirement),
            action = action,
        )
    }

    fun complete() {
        state.complete()
    }

    fun handle(error: LocationError) {
        state.handle(error)
    }

    fun registerCancellation(onCancel: () -> Unit) {
        state.registerCancellation(onCancel)
    }

    fun cancel() {
        state.cancel()
    }

    fun dismiss() {
        if (
            state.prompt == ForegroundLocationPrompt.PermissionRequired ||
            state.prompt == ForegroundLocationPrompt.PrecisePermissionRequired ||
            state.prompt == ForegroundLocationPrompt.PermissionDenied ||
            state.prompt == ForegroundLocationPrompt.PermissionPermanentlyDenied
        ) {
            onPermissionDenied()
        }
        state.dismiss()
    }

    fun performPrimaryAction() {
        val pendingPurpose = state.pendingPurpose ?: return
        when (state.prompt) {
            ForegroundLocationPrompt.PermissionRequired,
            ForegroundLocationPrompt.PrecisePermissionRequired,
            ForegroundLocationPrompt.PermissionDenied -> {
                state.beginPermissionRequest()
                permissionLauncher.launch(pendingPurpose)
            }
            ForegroundLocationPrompt.PermissionPermanentlyDenied -> {
                state.beginSettingsResolution()
                appSettingsLauncher.launch(PlatformSettingsDestination.ApplicationDetails)
            }
            ForegroundLocationPrompt.LocationServiceDisabled -> {
                state.beginLocationResolution()
                locationSettingsLauncher.launch(pendingPurpose)
            }
            ForegroundLocationPrompt.LocationUnavailable -> {
                state.retry(readiness(pendingPurpose.accuracyRequirement))
            }
            null -> Unit
        }
    }
}

@Composable
fun rememberForegroundLocationAccessCoordinator(
    enabled: Boolean,
    readiness: (LocationAccuracyRequirement) -> LocationReadiness,
    onPermissionDenied: () -> Unit = {},
    onDiagnosticEvent: (ForegroundLocationDiagnosticEvent) -> Unit = ::logLocationAccessEvent,
): ForegroundLocationAccessCoordinator {
    val currentReadiness by rememberUpdatedState(readiness)
    val currentOnPermissionDenied by rememberUpdatedState(onPermissionDenied)
    val currentOnDiagnosticEvent by rememberUpdatedState(onDiagnosticEvent)
    val state = remember(enabled) {
        ForegroundLocationAccessState { event -> currentOnDiagnosticEvent(event) }
    }

    fun pendingReadiness(): LocationReadiness {
        val requirement = state.pendingPurpose?.accuracyRequirement
            ?: LocationAccuracyRequirement.Approximate
        return currentReadiness(requirement)
    }

    val permissionLauncher = rememberForegroundLocationPermissionLauncher(
        enabled = enabled,
        onResult = { result -> state.handlePermissionResult(result, pendingReadiness()) },
    )
    val appSettingsLauncher = rememberPlatformSettingsLauncher(
        onReturned = { state.resumeApplicationSettings(pendingReadiness()) },
    )
    val locationSettingsLauncher = rememberForegroundLocationSettingsLauncher(
        onResult = { result -> state.handleLocationSettingsResult(result, pendingReadiness()) },
    )

    ObserveLocationCapabilityChanges {
        state.reconcile(pendingReadiness())
    }

    return remember(state, permissionLauncher, appSettingsLauncher, locationSettingsLauncher) {
        ForegroundLocationAccessCoordinator(
            state = state,
            readiness = { requirement -> currentReadiness(requirement) },
            permissionLauncher = permissionLauncher,
            appSettingsLauncher = appSettingsLauncher,
            locationSettingsLauncher = locationSettingsLauncher,
            onPermissionDenied = { currentOnPermissionDenied() },
        )
    }
}

internal class ForegroundLocationAccessState(
    private val onDiagnosticEvent: (ForegroundLocationDiagnosticEvent) -> Unit = {},
) {
    var prompt by mutableStateOf<ForegroundLocationPrompt?>(null)
        private set

    var progress by mutableStateOf(ForegroundLocationProgress.Idle)
        private set

    var pendingPurpose: ForegroundLocationPurpose? = null
        private set

    private var pendingAction: (() -> Unit)? = null
    private var cancellationAction: (() -> Unit)? = null

    fun request(
        purpose: ForegroundLocationPurpose,
        readiness: LocationReadiness,
        action: () -> Unit,
    ): Boolean {
        if (pendingAction != null || progress != ForegroundLocationProgress.Idle) {
            emit(purpose, ForegroundLocationDiagnosticStage.ConcurrentRequestIgnored)
            return false
        }
        pendingPurpose = purpose
        pendingAction = action
        emit(purpose, ForegroundLocationDiagnosticStage.RequestReceived)
        advance(readiness)
        return true
    }

    fun beginPermissionRequest() {
        val purpose = pendingPurpose ?: return
        progress = ForegroundLocationProgress.RequestingPermission
        emit(purpose, ForegroundLocationDiagnosticStage.PermissionRequestStarted)
    }

    fun handlePermissionResult(
        result: PermissionRequestResult,
        readiness: LocationReadiness,
    ) {
        handlePermissionResult(
            status = result.status,
            unavailableReason = result.unavailableReason,
            readiness = readiness,
        )
    }

    fun handlePermissionResult(
        status: PlatformPermissionStatus,
        readiness: LocationReadiness,
        unavailableReason: PlatformPermissionUnavailableReason? = null,
    ) {
        val purpose = pendingPurpose ?: return
        progress = ForegroundLocationProgress.Idle
        when (status) {
            PlatformPermissionStatus.Granted,
            PlatformPermissionStatus.NotRequired -> {
                emit(purpose, ForegroundLocationDiagnosticStage.PermissionGranted)
                advance(readiness)
            }
            PlatformPermissionStatus.PermanentlyDenied -> {
                prompt = ForegroundLocationPrompt.PermissionPermanentlyDenied
                emit(purpose, ForegroundLocationDiagnosticStage.PermissionPermanentlyDenied)
            }
            PlatformPermissionStatus.Denied,
            PlatformPermissionStatus.NotDetermined -> {
                prompt = ForegroundLocationPrompt.PermissionDenied
                emit(purpose, ForegroundLocationDiagnosticStage.PermissionDenied)
            }
            PlatformPermissionStatus.Unavailable -> {
                prompt = ForegroundLocationPrompt.LocationUnavailable
                emit(
                    purpose = purpose,
                    stage = ForegroundLocationDiagnosticStage.PermissionUnavailable,
                    unavailableReason = unavailableReason,
                )
            }
        }
    }

    fun beginSettingsResolution() {
        progress = ForegroundLocationProgress.ResolvingSettings
    }

    fun beginLocationResolution() {
        val purpose = pendingPurpose ?: return
        beginSettingsResolution()
        emit(purpose, ForegroundLocationDiagnosticStage.LocationResolutionStarted)
    }

    fun resumeApplicationSettings(readiness: LocationReadiness) {
        progress = ForegroundLocationProgress.Idle
        if (readiness is LocationReadiness.PermissionRequired) {
            prompt = ForegroundLocationPrompt.PermissionPermanentlyDenied
        } else {
            advance(readiness)
        }
    }

    fun handleLocationSettingsResult(
        result: LocationSettingsResolutionResult,
        readiness: LocationReadiness,
    ) {
        val purpose = pendingPurpose ?: return
        progress = ForegroundLocationProgress.Idle
        when (result) {
            LocationSettingsResolutionResult.Satisfied,
            LocationSettingsResolutionResult.Changed,
            LocationSettingsResolutionResult.ReturnedFromSettings -> {
                emit(purpose, ForegroundLocationDiagnosticStage.LocationSettingsChanged)
                advance(readiness)
            }
            LocationSettingsResolutionResult.Declined -> {
                prompt = ForegroundLocationPrompt.LocationServiceDisabled
                emit(purpose, ForegroundLocationDiagnosticStage.LocationSettingsDeclined)
            }
            LocationSettingsResolutionResult.Unavailable -> {
                prompt = ForegroundLocationPrompt.LocationUnavailable
                emit(purpose, ForegroundLocationDiagnosticStage.LocationSettingsUnavailable)
            }
        }
    }

    fun reconcile(readiness: LocationReadiness) {
        if (pendingAction == null || progress != ForegroundLocationProgress.Idle) return
        if (
            readiness is LocationReadiness.PermissionRequired &&
            (prompt == ForegroundLocationPrompt.PermissionDenied ||
                prompt == ForegroundLocationPrompt.PermissionPermanentlyDenied)
        ) {
            return
        }
        advance(readiness)
    }

    fun complete() {
        pendingPurpose?.let { emit(it, ForegroundLocationDiagnosticStage.ActionCompleted) }
        clear()
    }

    fun registerCancellation(onCancel: () -> Unit) {
        if (progress == ForegroundLocationProgress.Executing) {
            cancellationAction = onCancel
        }
    }

    fun cancel() {
        val purpose = pendingPurpose ?: return
        runCatching { cancellationAction?.invoke() }
        emit(purpose, ForegroundLocationDiagnosticStage.Cancelled)
        clear()
    }

    fun handle(error: LocationError) {
        val purpose = pendingPurpose ?: return
        progress = ForegroundLocationProgress.Idle
        prompt = when (error) {
            LocationError.PermissionDenied -> {
                emit(purpose, ForegroundLocationDiagnosticStage.ActionFailedPermission)
                ForegroundLocationPrompt.PermissionRequired
            }
            LocationError.GpsDisabled -> {
                emit(purpose, ForegroundLocationDiagnosticStage.ActionFailedService)
                ForegroundLocationPrompt.LocationServiceDisabled
            }
            LocationError.Unsupported -> {
                emit(purpose, ForegroundLocationDiagnosticStage.ActionFailedUnsupported)
                ForegroundLocationPrompt.LocationUnavailable
            }
            LocationError.Timeout -> {
                emit(purpose, ForegroundLocationDiagnosticStage.ActionFailedTimeout)
                ForegroundLocationPrompt.LocationUnavailable
            }
            is LocationError.Unknown -> {
                emit(purpose, ForegroundLocationDiagnosticStage.ActionFailedUnknown)
                ForegroundLocationPrompt.LocationUnavailable
            }
        }
    }

    fun retry(readiness: LocationReadiness) {
        progress = ForegroundLocationProgress.Idle
        advance(readiness)
    }

    fun dismiss() {
        pendingPurpose?.let { emit(it, ForegroundLocationDiagnosticStage.Dismissed) }
        clear()
    }

    private fun advance(readiness: LocationReadiness) {
        val purpose = pendingPurpose ?: return
        when (readiness) {
            is LocationReadiness.PermissionRequired -> {
                progress = ForegroundLocationProgress.Idle
                prompt = if (
                    readiness.requirement == LocationAccuracyRequirement.Precise &&
                    readiness.grantedAccuracy.satisfies(LocationAccuracyRequirement.Approximate)
                ) {
                    emit(purpose, ForegroundLocationDiagnosticStage.PrecisePermissionPromptShown)
                    ForegroundLocationPrompt.PrecisePermissionRequired
                } else {
                    emit(purpose, ForegroundLocationDiagnosticStage.PermissionPromptShown)
                    ForegroundLocationPrompt.PermissionRequired
                }
            }
            LocationReadiness.LocationServiceDisabled -> {
                progress = ForegroundLocationProgress.Idle
                prompt = ForegroundLocationPrompt.LocationServiceDisabled
                emit(purpose, ForegroundLocationDiagnosticStage.LocationServicePromptShown)
            }
            LocationReadiness.Ready -> executePendingAction(purpose)
        }
    }

    private fun executePendingAction(purpose: ForegroundLocationPurpose) {
        if (progress == ForegroundLocationProgress.Executing) return
        prompt = null
        progress = ForegroundLocationProgress.Executing
        emit(purpose, ForegroundLocationDiagnosticStage.ActionStarted)
        try {
            pendingAction?.invoke()
        } catch (_: Throwable) {
            progress = ForegroundLocationProgress.Idle
            prompt = ForegroundLocationPrompt.LocationUnavailable
            emit(purpose, ForegroundLocationDiagnosticStage.ActionFailedUnknown)
        }
    }

    private fun clear() {
        progress = ForegroundLocationProgress.Idle
        pendingAction = null
        cancellationAction = null
        pendingPurpose = null
        prompt = null
    }

    private fun emit(
        purpose: ForegroundLocationPurpose,
        stage: ForegroundLocationDiagnosticStage,
        unavailableReason: PlatformPermissionUnavailableReason? = null,
    ) {
        onDiagnosticEvent(
            ForegroundLocationDiagnosticEvent(
                purpose = purpose,
                stage = stage,
                unavailableReason = unavailableReason,
            ),
        )
    }
}

private fun logLocationAccessEvent(event: ForegroundLocationDiagnosticEvent) {
    println("LocationAccess: purpose=${event.purpose.name} stage=${event.stage.name} unavailableReason=${event.unavailableReason?.name ?: "none"}")
}
