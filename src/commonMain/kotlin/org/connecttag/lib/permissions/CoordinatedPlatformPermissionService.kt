package org.connecttag.lib.permissions

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Adds one global prompt queue, same-permission request coalescing, and a
 * status preflight to a platform permission backend.
 */
fun createCoordinatedPlatformPermissionService(
    delegate: PlatformPermissionService,
    promptQueueWaitTimeoutMillis: Long = DEFAULT_PERMISSION_PROMPT_QUEUE_WAIT_TIMEOUT_MILLIS,
): PlatformPermissionService = CoordinatedPlatformPermissionService(
    delegate = delegate,
    promptQueueWaitTimeoutMillis = promptQueueWaitTimeoutMillis,
)

private class CoordinatedPlatformPermissionService(
    private val delegate: PlatformPermissionService,
    private val promptQueueWaitTimeoutMillis: Long,
) : PlatformPermissionService {
    init {
        require(promptQueueWaitTimeoutMillis > 0) {
            "promptQueueWaitTimeoutMillis must be greater than zero."
        }
    }

    private val promptQueueMutex = Mutex()
    private val inFlightMutex = Mutex()
    private val inFlightRequests = mutableMapOf<
        PlatformPermission,
        CompletableDeferred<PermissionRequestResult>,
    >()

    override suspend fun status(permission: PlatformPermission): PlatformPermissionStatus {
        return current(permission).status
    }

    override suspend fun current(permission: PlatformPermission): PermissionRequestResult {
        return try {
            delegate.current(permission).withDefaultUnavailableReason()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            permission.unavailableResult(
                PlatformPermissionUnavailableReason.ProviderFailure,
            )
        }
    }

    override suspend fun request(permission: PlatformPermission): PermissionRequestResult {
        var ownsRequest = false
        val sharedResult = inFlightMutex.withLock {
            inFlightRequests[permission] ?: CompletableDeferred<PermissionRequestResult>().also {
                inFlightRequests[permission] = it
                ownsRequest = true
            }
        }
        if (!ownsRequest) {
            return withTimeoutOrNull(promptQueueWaitTimeoutMillis) {
                sharedResult.await()
            } ?: permission.unavailableResult(
                PlatformPermissionUnavailableReason.TimedOut,
            )
        }

        return try {
            val result = requestAfterQueueWait(permission)
            sharedResult.complete(result)
            result
        } catch (cancellation: CancellationException) {
            sharedResult.cancel(cancellation)
            throw cancellation
        } catch (_: Throwable) {
            val result = permission.unavailableResult(
                PlatformPermissionUnavailableReason.ProviderFailure,
            )
            sharedResult.complete(result)
            result
        } finally {
            withContext(NonCancellable) {
                inFlightMutex.withLock {
                    if (inFlightRequests[permission] === sharedResult) {
                        inFlightRequests.remove(permission)
                    }
                }
            }
        }
    }

    override fun openSettings(): PlatformSettingsOpenResult {
        return try {
            delegate.openSettings()
        } catch (_: Throwable) {
            PlatformSettingsOpenResult.Failed
        }
    }

    private suspend fun requestAfterQueueWait(
        permission: PlatformPermission,
    ): PermissionRequestResult {
        val acquired = withTimeoutOrNull(promptQueueWaitTimeoutMillis) {
            promptQueueMutex.lock()
            true
        } ?: false
        if (!acquired) {
            return permission.unavailableResult(
                PlatformPermissionUnavailableReason.TimedOut,
            )
        }

        return try {
            requestAfterPreflight(permission)
        } finally {
            promptQueueMutex.unlock()
        }
    }

    private suspend fun requestAfterPreflight(
        permission: PlatformPermission,
    ): PermissionRequestResult {
        val current = current(permission)
        return when (current.status) {
            PlatformPermissionStatus.Granted,
            PlatformPermissionStatus.NotRequired,
            PlatformPermissionStatus.PermanentlyDenied -> {
                current
            }
            PlatformPermissionStatus.Unavailable -> {
                current
            }
            PlatformPermissionStatus.Denied,
            PlatformPermissionStatus.NotDetermined -> {
                delegate.request(permission).withDefaultUnavailableReason()
            }
        }
    }
}

private fun PermissionRequestResult.withDefaultUnavailableReason(): PermissionRequestResult {
    if (
        status != PlatformPermissionStatus.Unavailable ||
        unavailableReason != null
    ) {
        return this
    }
    return copy(unavailableReason = PlatformPermissionUnavailableReason.ProviderFailure)
}

internal fun PlatformPermission.unavailableResult(
    reason: PlatformPermissionUnavailableReason,
): PermissionRequestResult {
    return PermissionRequestResult(
        permission = this,
        status = PlatformPermissionStatus.Unavailable,
        unavailableReason = reason,
    )
}
