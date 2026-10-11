package io.github.aeginventor.ledgerguard.validation;

/**
 * 검증이 실행되는 단계. DRAFT는 초안(LLM 또는 fixture) 결과를 저장 없이 검증할 때, SAVE는 전표 저장과 승인 시점이다.
 */
public enum Stage {
    DRAFT, SAVE
}
