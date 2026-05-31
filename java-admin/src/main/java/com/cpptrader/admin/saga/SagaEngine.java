package com.cpptrader.admin.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SagaEngine {

    private final SagaInstanceRepository sagaInstanceRepository;
    private final SagaStepLogRepository sagaStepLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public SagaContext execute(SagaDefinition definition) {
        String sagaId = UUID.randomUUID().toString();

        List<SagaInstance> runningInstances = sagaInstanceRepository.findBySagaNameAndStatus(
            definition.getSagaName(), SagaInstance.SagaStatus.RUNNING.getValue());
        if (!runningInstances.isEmpty()) {
            throw new RuntimeException("Saga already running: " + definition.getSagaName());
        }

        SagaContext context = new SagaContext(sagaId);

        SagaInstance instance = new SagaInstance();
        instance.setSagaName(definition.getSagaName());
        instance.setSagaId(sagaId);
        instance.setStatus(SagaInstance.SagaStatus.RUNNING.getValue());
        instance.setCurrentStep(0);
        instance.setContext("{}");
        sagaInstanceRepository.save(instance);

        log.info("Saga started: name={}, sagaId={}", definition.getSagaName(), sagaId);

        List<SagaStep> steps = definition.getSteps();
        SagaStepResult lastResult = SagaStepResult.success();
        int failedStepIndex = -1;

        for (int i = 0; i < steps.size(); i++) {
            SagaStep step = steps.get(i);
            instance.setCurrentStep(i);
            sagaInstanceRepository.save(instance);

            SagaStepLog stepLog = new SagaStepLog();
            stepLog.setSagaId(sagaId);
            stepLog.setStepName(step.getStepName());
            stepLog.setStepOrder(i);
            stepLog.setStatus(SagaStepLog.StepStatus.PENDING.getValue());
            sagaStepLogRepository.save(stepLog);

            try {
                log.info("Executing step {}/{}: {}", i + 1, steps.size(), step.getStepName());
                lastResult = step.getAction().apply(context, lastResult);
                if (lastResult.isSuccess()) {
                    stepLog.setStatus(SagaStepLog.StepStatus.SUCCESS.getValue());
                    stepLog.setResponseData(serialize(lastResult.getData()));
                    sagaStepLogRepository.save(stepLog);
                } else {
                    stepLog.setStatus(SagaStepLog.StepStatus.FAILED.getValue());
                    stepLog.setResponseData(lastResult.getMessage());
                    sagaStepLogRepository.save(stepLog);
                    failedStepIndex = i;
                    break;
                }
            } catch (Exception e) {
                log.error("Step {} execution failed: {}", step.getStepName(), e.getMessage(), e);
                stepLog.setStatus(SagaStepLog.StepStatus.FAILED.getValue());
                stepLog.setResponseData(e.getMessage());
                sagaStepLogRepository.save(stepLog);
                lastResult = SagaStepResult.failure(e.getMessage());
                failedStepIndex = i;
                break;
            }
        }

        if (failedStepIndex >= 0) {
            compensate(definition, context, instance, failedStepIndex);
        } else {
            instance.setStatus(SagaInstance.SagaStatus.COMPLETED.getValue());
            sagaInstanceRepository.save(instance);
            log.info("Saga completed: sagaId={}", sagaId);
        }

        return context;
    }

    private void compensate(SagaDefinition definition, SagaContext context, SagaInstance instance, int failedStepIndex) {
        instance.setStatus(SagaInstance.SagaStatus.COMPENSATING.getValue());
        sagaInstanceRepository.save(instance);

        log.info("Starting compensation for saga: sagaId={}, failedStep={}", instance.getSagaId(), failedStepIndex);

        boolean compensateFailed = false;
        for (int i = failedStepIndex - 1; i >= 0; i--) {
            SagaStep step = definition.getSteps().get(i);
            boolean stepCompensated = false;
            int maxRetries = 3;

            for (int retry = 0; retry <= maxRetries; retry++) {
                try {
                    log.info("Compensating step {}: {} (attempt {}/{})", i, step.getStepName(), retry + 1, maxRetries + 1);
                    SagaStepResult result = step.getCompensate().apply(context, SagaStepResult.success());

                    List<SagaStepLog> logs = sagaStepLogRepository.findBySagaIdOrderByStepOrderAsc(instance.getSagaId());
                    if (i < logs.size()) {
                        SagaStepLog stepLog = logs.get(i);
                        stepLog.setStatus(result.isSuccess() ? SagaStepLog.StepStatus.COMPENSATED.getValue() : SagaStepLog.StepStatus.COMPENSATE_FAILED.getValue());
                        sagaStepLogRepository.save(stepLog);
                    }

                    if (result.isSuccess()) {
                        stepCompensated = true;
                        break;
                    }

                    if (retry < maxRetries) {
                        Thread.sleep(100L * (retry + 1));
                    }
                } catch (Exception e) {
                    log.error("Compensation exception for step {} (attempt {}): {}", step.getStepName(), retry + 1, e.getMessage(), e);

                    List<SagaStepLog> logs = sagaStepLogRepository.findBySagaIdOrderByStepOrderAsc(instance.getSagaId());
                    if (i < logs.size()) {
                        SagaStepLog stepLog = logs.get(i);
                        stepLog.setStatus(SagaStepLog.StepStatus.COMPENSATE_FAILED.getValue());
                        stepLog.setResponseData(e.getMessage());
                        sagaStepLogRepository.save(stepLog);
                    }

                    if (retry < maxRetries) {
                        try { Thread.sleep(100L * (retry + 1)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    }
                }
            }

            if (!stepCompensated) {
                compensateFailed = true;
                log.error("Compensation failed for step after {} retries: {}", maxRetries + 1, step.getStepName());
            }
        }

        instance.setStatus(compensateFailed ? SagaInstance.SagaStatus.COMPENSATE_FAILED.getValue() : SagaInstance.SagaStatus.COMPENSATED.getValue());
        sagaInstanceRepository.save(instance);

        if (compensateFailed) {
            log.error("Saga compensation partially failed, manual intervention required: sagaId={}", instance.getSagaId());
        } else {
            log.info("Saga compensation completed: sagaId={}", instance.getSagaId());
        }
    }

    private String serialize(Object obj) {
        try {
            return obj == null ? null : objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }
}
