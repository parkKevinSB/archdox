package com.archdox.cloud.worker;

import com.archdox.worker.application.ArchDoxWorkerActionExecutor;
import com.archdox.worker.application.ArchDoxWorkerActionRegistry;
import com.archdox.worker.application.ArchDoxWorkerPolicyGate;
import com.archdox.worker.application.ArchDoxWorkerTraceSink;
import com.archdox.worker.flow.ArchDoxWorkerExecutionFlowFactory;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ArchDoxWorkerConfiguration {
    @Bean
    @ConditionalOnMissingBean
    ArchDoxWorkerTraceSink archDoxWorkerTraceSink() {
        return ArchDoxWorkerTraceSink.noop();
    }

    @Bean
    @ConditionalOnMissingBean
    ArchDoxWorkerPolicyGate archDoxWorkerPolicyGate() {
        return ArchDoxWorkerPolicyGate.denyAll(
                "ARCHDOX_WORKER_DISABLED",
                "ArchDox worker actions are not enabled yet");
    }

    @Bean
    ArchDoxWorkerActionRegistry archDoxWorkerActionRegistry(List<ArchDoxWorkerActionExecutor> executors) {
        return new ArchDoxWorkerActionRegistry(executors);
    }

    @Bean
    ArchDoxWorkerExecutionFlowFactory archDoxWorkerExecutionFlowFactory(
            ArchDoxWorkerActionRegistry registry,
            ArchDoxWorkerPolicyGate policyGate,
            ArchDoxWorkerTraceSink traceSink
    ) {
        return new ArchDoxWorkerExecutionFlowFactory(registry, policyGate, traceSink);
    }
}
