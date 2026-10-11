package io.github.aeginventor.ledgerguard.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BusinessNumberValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"1234567891", "9876543215", "1112223339", "5550001116", "2468135797", "3141592650"})
    void acceptsNumbersGeneratedByTheChecksumAlgorithm(String bizNo) {
        assertThat(BusinessNumberValidator.isValid(bizNo)).isTrue();
    }

    @Test
    void acceptsHyphenatedFormat() {
        assertThat(BusinessNumberValidator.isValid("123-45-67891")).isTrue();
        assertThat(BusinessNumberValidator.normalize("123-45-67891")).isEqualTo("1234567891");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890", "1234567892", "9876543210", "0000000001"})
    void rejectsWrongCheckDigit(String bizNo) {
        assertThat(BusinessNumberValidator.isValid(bizNo)).isFalse();
    }

    @Test
    void rejectsMalformedInput() {
        assertThat(BusinessNumberValidator.isValid(null)).isFalse();
        assertThat(BusinessNumberValidator.isValid("")).isFalse();
        assertThat(BusinessNumberValidator.isValid("123456789")).isFalse();
        assertThat(BusinessNumberValidator.isValid("12345678901")).isFalse();
        assertThat(BusinessNumberValidator.isValid("12345678ab")).isFalse();
        assertThat(BusinessNumberValidator.normalize("12-34")).isNull();
    }

    @Test
    void checkDigitFollowsTheWeightedSumRule() {
        // 앞 9자리 123456789: 가중합 = 1+6+21+4+15+42+7+24+45 = 165, 9×5/10 = 4, 합 169, (10-9)%10 = 1
        assertThat(BusinessNumberValidator.isValid("1234567891")).isTrue();
    }
}
