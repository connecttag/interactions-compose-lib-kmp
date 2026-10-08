package org.connecttag.lib.permissions

enum class PlatformPermissionNextAction {
    Proceed,
    ExplainThenRequest,
    Request,
    OpenSettings,
    WaitForCooldown,
    Unavailable,
}

data class PlatformPermissionRequestHistory(
    val requestCount: Int = 0,
    val denialCount: Int = 0,
    val lastDeniedAtMillis: Long? = null,
) {
    init {
        require(requestCount >= 0) { "requestCount must not be negative." }
        require(denialCount >= 0) { "denialCount must not be negative." }
        require(denialCount <= requestCount) {
            "denialCount must not exceed requestCount."
        }
    }

    fun record(
        result: PermissionRequestResult,
        recordedAtMillis: Long,
    ): PlatformPermissionRequestHistory {
        val denied = result.status == PlatformPermissionStatus.Denied ||
            result.status == PlatformPermissionStatus.PermanentlyDenied
        return copy(
            requestCount = requestCount + 1,
            denialCount = denialCount + if (denied) 1 else 0,
            lastDeniedAtMillis = recordedAtMillis.takeIf { denied } ?: lastDeniedAtMillis,
        )
    }
}

data class PlatformPermissionRationaleDecision(
    val action: PlatformPermissionNextAction,
    val retryAfterMillis: Long? = null,
) {
    init {
        require(
            (retryAfterMillis != null) ==
                (action == PlatformPermissionNextAction.WaitForCooldown),
        ) {
            "WaitForCooldown requires retryAfterMillis and no other action accepts it."
        }
        require(retryAfterMillis == null || retryAfterMillis > 0) {
            "retryAfterMillis must be greater than zero."
        }
    }

    val requiresExplanation: Boolean
        get() = action == PlatformPermissionNextAction.ExplainThenRequest

    val canRequest: Boolean
        get() = action == PlatformPermissionNextAction.Request ||
            action == PlatformPermissionNextAction.ExplainThenRequest

    val canOpenSettings: Boolean
        get() = action == PlatformPermissionNextAction.OpenSettings
}

/**
 * Product-neutral decision policy. The consuming UI owns localized rationale
 * copy and decides when an explanation has been acknowledged.
 */
data class PlatformPermissionRationalePolicy(
    val explainBeforeFirstRequest: Boolean = true,
    val explainAfterDenial: Boolean = true,
    val denialCooldownMillis: Long = DEFAULT_PERMISSION_DENIAL_COOLDOWN_MILLIS,
) {
    init {
        require(denialCooldownMillis >= 0) {
            "denialCooldownMillis must not be negative."
        }
    }

    fun evaluate(
        result: PermissionRequestResult,
        history: PlatformPermissionRequestHistory = PlatformPermissionRequestHistory(),
        nowMillis: Long,
    ): PlatformPermissionRationaleDecision {
        return when (result.status) {
            PlatformPermissionStatus.Granted,
            PlatformPermissionStatus.NotRequired -> {
                decision(PlatformPermissionNextAction.Proceed)
            }
            PlatformPermissionStatus.PermanentlyDenied -> {
                decision(PlatformPermissionNextAction.OpenSettings)
            }
            PlatformPermissionStatus.Unavailable -> {
                decision(PlatformPermissionNextAction.Unavailable)
            }
            PlatformPermissionStatus.NotDetermined -> {
                decision(
                    if (explainBeforeFirstRequest && history.requestCount == 0) {
                        PlatformPermissionNextAction.ExplainThenRequest
                    } else {
                        PlatformPermissionNextAction.Request
                    },
                )
            }
            PlatformPermissionStatus.Denied -> {
                val retryAfterMillis = remainingCooldownMillis(
                    lastDeniedAtMillis = history.lastDeniedAtMillis,
                    nowMillis = nowMillis,
                )
                if (retryAfterMillis > 0) {
                    PlatformPermissionRationaleDecision(
                        action = PlatformPermissionNextAction.WaitForCooldown,
                        retryAfterMillis = retryAfterMillis,
                    )
                } else {
                    decision(
                        if (explainAfterDenial) {
                            PlatformPermissionNextAction.ExplainThenRequest
                        } else {
                            PlatformPermissionNextAction.Request
                        },
                    )
                }
            }
        }
    }

    private fun remainingCooldownMillis(
        lastDeniedAtMillis: Long?,
        nowMillis: Long,
    ): Long {
        val deniedAt = lastDeniedAtMillis ?: return 0L
        if (denialCooldownMillis == 0L) return 0L
        if (nowMillis <= deniedAt) return denialCooldownMillis
        val elapsed = (nowMillis - deniedAt).coerceAtMost(denialCooldownMillis)
        return denialCooldownMillis - elapsed
    }

    private fun decision(
        action: PlatformPermissionNextAction,
    ): PlatformPermissionRationaleDecision {
        return PlatformPermissionRationaleDecision(action)
    }
}

const val DEFAULT_PERMISSION_DENIAL_COOLDOWN_MILLIS: Long = 30_000L
