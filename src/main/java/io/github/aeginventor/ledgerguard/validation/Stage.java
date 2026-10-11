package io.github.aeginventor.ledgerguard.validation;

/**
 * 검증이 실행되는 단계. DRAFT는 초안(LLM 또는 fixture) 결과를 저장 없이 검증할 때, SAVE는 전표 저장 시점,
 * APPROVE는 승인 시점이다. 승인 단계에서는 저장 단계의 규칙을 모두 다시 돌리고 R12(직무 분리)를 더한다.
 */
public enum Stage {
    DRAFT, SAVE, APPROVE
}
