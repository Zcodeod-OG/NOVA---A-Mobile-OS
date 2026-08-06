package com.nova.runtime.events.policy

/** Policy engine events per EMS module taxonomy. */
object PolicyEvents {
    const val EVALUATION_STARTED = "PolicyEvaluationStarted"
    const val EVALUATION_COMPLETED = "PolicyEvaluationCompleted"
    const val ACTION_DENIED = "PolicyActionDenied"
    const val CONFIRMATION_REQUIRED = "PolicyConfirmationRequired"
}
