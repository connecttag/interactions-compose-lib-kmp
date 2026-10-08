package org.connecttag.lib.permissions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ReactivePlatformPermissionStore(
    private val service: PlatformPermissionService? = null,
    permissions: Set<PlatformPermission> = PlatformPermission.entries.toSet(),
    initialStatus: PlatformPermissionStatus = PlatformPermissionStatus.NotDetermined,
    initialUnavailableReason: PlatformPermissionUnavailableReason? = null,
) {
    private val refreshAllMutex = Mutex()
    private val statusFlows = permissions
        .associateWith { MutableStateFlow(initialStatus) }
        .toMutableMap()
    private val grantedFlows = permissions
        .associateWith { MutableStateFlow(initialStatus.isGrantedOrNotRequired()) }
        .toMutableMap()
    private val resultFlows = permissions
        .associateWith { permission ->
            MutableStateFlow(
                PermissionRequestResult(
                    permission = permission,
                    status = initialStatus,
                    unavailableReason = initialUnavailableReason,
                ),
            )
        }
        .toMutableMap()

    private val _permissionsState = MutableStateFlow(statusFlows.mapValues { it.value.value })
    val permissionsState: StateFlow<Map<PlatformPermission, PlatformPermissionStatus>> =
        _permissionsState.asStateFlow()
    private val _permissionResultsState = MutableStateFlow(
        resultFlows.mapValues { it.value.value },
    )
    val permissionResultsState: StateFlow<Map<PlatformPermission, PermissionRequestResult>> =
        _permissionResultsState.asStateFlow()

    fun status(permission: PlatformPermission): StateFlow<PlatformPermissionStatus> {
        return statusFlow(permission).asStateFlow()
    }

    fun isGranted(permission: PlatformPermission): StateFlow<Boolean> {
        return grantedFlow(permission).asStateFlow()
    }

    fun result(permission: PlatformPermission): StateFlow<PermissionRequestResult> {
        return resultFlow(permission).asStateFlow()
    }

    suspend fun refresh(permission: PlatformPermission): PlatformPermissionStatus {
        val result = requireService().current(permission)
        updateResult(result)
        return result.status
    }

    suspend fun refreshAll(): Map<PlatformPermission, PlatformPermissionStatus> {
        return refreshAllMutex.withLock {
            val permissions = statusFlows.keys.toList()
            permissions.forEach { permission -> refresh(permission) }
            permissionsState.value
        }
    }

    suspend fun request(permission: PlatformPermission): PermissionRequestResult {
        val result = requireService().request(permission)
        updateResult(result)
        return result
    }

    fun updateStatus(
        permission: PlatformPermission,
        status: PlatformPermissionStatus,
    ) {
        updateResult(PermissionRequestResult(permission, status))
    }

    fun updateResult(result: PermissionRequestResult) {
        statusFlow(result.permission).value = result.status
        grantedFlow(result.permission).value = result.status.isGrantedOrNotRequired()
        resultFlow(result.permission).value = result
        _permissionsState.update { current ->
            current + (result.permission to result.status)
        }
        _permissionResultsState.update { current ->
            current + (result.permission to result)
        }
    }

    private fun requireService(): PlatformPermissionService {
        return service ?: error("PlatformPermissionService is required for refresh/request operations.")
    }

    private fun statusFlow(permission: PlatformPermission): MutableStateFlow<PlatformPermissionStatus> {
        return statusFlows.getOrPut(permission) {
            MutableStateFlow(PlatformPermissionStatus.NotDetermined)
        }
    }

    private fun grantedFlow(permission: PlatformPermission): MutableStateFlow<Boolean> {
        return grantedFlows.getOrPut(permission) {
            MutableStateFlow(false)
        }
    }

    private fun resultFlow(
        permission: PlatformPermission,
    ): MutableStateFlow<PermissionRequestResult> {
        return resultFlows.getOrPut(permission) {
            MutableStateFlow(
                PermissionRequestResult(
                    permission = permission,
                    status = PlatformPermissionStatus.NotDetermined,
                ),
            )
        }
    }
}
