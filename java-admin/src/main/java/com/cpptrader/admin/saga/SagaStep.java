package com.cpptrader.admin.saga;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.function.BiFunction;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SagaStep {
    private String stepName;
    private BiFunction<SagaContext, SagaStepResult, SagaStepResult> action;
    private BiFunction<SagaContext, SagaStepResult, SagaStepResult> compensate;
}
