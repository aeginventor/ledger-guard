package io.github.aeginventor.ledgerguard.validation;

import java.util.List;

/**
 * 전표 검증 규칙 하나. 규칙은 서로 독립이며 엔진이 코드 순으로 모두 실행한다.
 */
public interface ValidationRule {

    String code();

    String name();

    Severity severity();

    List<Finding> check(ValidationContext context);
}
