package com.cpptrader.admin.saga;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SagaStepResult {
    private boolean success;
    private String message;
    private Object data;

    public static SagaStepResult success() {
        return new SagaStepResult(true, "OK", null);
    }

    public static SagaStepResult success(Object data) {
        return new SagaStepResult(true, "OK", data);
    }

    public static SagaStepResult failure(String message) {
        return new SagaStepResult(false, message, null);
    }
}
