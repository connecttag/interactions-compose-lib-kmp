package org.connecttag.lib.permissions

data class PlatformPermissionRuntimeConfig(
    val trackedPermissions: Set<PlatformPermission> = PlatformPermission.entries.toSet(),
    val promptQueueWaitTimeoutMillis: Long = DEFAULT_PERMISSION_PROMPT_QUEUE_WAIT_TIMEOUT_MILLIS,
    val rationalePolicy: PlatformPermissionRationalePolicy = PlatformPermissionRationalePolicy(),
) {
    init {
        require(promptQueueWaitTimeoutMillis > 0) {
            "promptQueueWaitTimeoutMillis must be greater than zero."
        }
    }
}

const val DEFAULT_PERMISSION_PROMPT_QUEUE_WAIT_TIMEOUT_MILLIS: Long = 15_000L
