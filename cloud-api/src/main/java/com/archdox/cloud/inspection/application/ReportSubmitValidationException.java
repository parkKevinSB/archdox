package com.archdox.cloud.inspection.application;

import com.archdox.cloud.inspection.dto.ReportSubmitValidationResponse;

public class ReportSubmitValidationException extends RuntimeException {
    private final ReportSubmitValidationResponse validation;

    public ReportSubmitValidationException(ReportSubmitValidationResponse validation) {
        super(validation.message());
        this.validation = validation;
    }

    public ReportSubmitValidationResponse validation() {
        return validation;
    }
}
