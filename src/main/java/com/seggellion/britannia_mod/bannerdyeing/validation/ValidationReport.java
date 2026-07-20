package com.seggellion.britannia_mod.bannerdyeing.validation;

import java.util.List;

public record ValidationReport(List<ValidationIssue> issues, ValidationSummary summary) {
    public ValidationReport {
        issues = issues.stream().distinct().sorted(ValidationIssue.ORDER).toList();
    }

    public boolean hasErrors() {
        return summary.errors() > 0;
    }

    public List<ValidationIssue> structuralIssues() {
        return issues.stream().filter(issue -> issue.stage() == ValidationStage.STRUCTURAL_DECODING).toList();
    }

    public List<ValidationIssue> crossReferenceIssues() {
        return issues.stream().filter(issue -> issue.stage() == ValidationStage.CROSS_REFERENCE).toList();
    }
}
