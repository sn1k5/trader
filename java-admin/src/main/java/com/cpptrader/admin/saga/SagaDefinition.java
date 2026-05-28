package com.cpptrader.admin.saga;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SagaDefinition {
    private String sagaName;
    private final List<SagaStep> steps = new ArrayList<>();

    public SagaDefinition(String sagaName) {
        this.sagaName = sagaName;
    }

    public SagaDefinition addStep(String stepName,
                                   java.util.function.BiFunction<SagaContext, SagaStepResult, SagaStepResult> action,
                                   java.util.function.BiFunction<SagaContext, SagaStepResult, SagaStepResult> compensate) {
        steps.add(new SagaStep(stepName, action, compensate));
        return this;
    }
}
