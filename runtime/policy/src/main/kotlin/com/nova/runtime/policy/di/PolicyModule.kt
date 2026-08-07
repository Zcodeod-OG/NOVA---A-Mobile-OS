package com.nova.runtime.policy.di

import com.nova.runtime.policy.PolicyEngine
import com.nova.runtime.policy.PolicyEngineImpl
import com.nova.runtime.policy.context.DefaultPolicyEnvironment
import com.nova.runtime.policy.context.PolicyEnvironment
import com.nova.runtime.policy.evaluator.BatteryPolicyEvaluator
import com.nova.runtime.policy.evaluator.ConfirmationPolicyEvaluator
import com.nova.runtime.policy.evaluator.PermissionPolicyEvaluator
import com.nova.runtime.policy.evaluator.PrivacyPolicyEvaluator
import com.nova.runtime.policy.evaluator.SafetyPolicyEvaluator
import com.nova.runtime.policy.events.PolicyEventPublisher
import com.nova.runtime.models.contracts.ActionPolicyGate
import org.koin.dsl.module

/** Koin DI wiring for Policy Engine per MSP §10. */
val policyModule = module {
    single<PolicyEnvironment> { DefaultPolicyEnvironment() }
    single { PolicyEventPublisher(get()) }
    single<PolicyEngine> {
        PolicyEngineImpl(
            evaluators = listOf(
                PermissionPolicyEvaluator(get()),
                SafetyPolicyEvaluator(),
                ConfirmationPolicyEvaluator(),
                PrivacyPolicyEvaluator(),
                BatteryPolicyEvaluator(get()),
            ),
            eventPublisher = get(),
            logger = get(),
        )
    }
    single<ActionPolicyGate> { get<PolicyEngine>() }
}
