package io.github.aeginventor.ledgerguard.validation;

/**
 * 사업자등록번호 체크섬. 앞 9자리에 가중치 1,3,7,1,3,7,1,3,5를 곱해 더하고,
 * 9번째 자리와 5의 곱을 10으로 나눈 몫을 더한 뒤, (10 - 합 % 10) % 10 이 10번째 자리와 같아야 한다.
 */
public final class BusinessNumberValidator {

    private static final int[] WEIGHTS = {1, 3, 7, 1, 3, 7, 1, 3, 5};

    private BusinessNumberValidator() {
    }

    /** 하이픈과 공백을 제거한 10자리 숫자만 돌려준다. 형식이 다르면 null. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replace("-", "").replace(" ", "");
        return digits.matches("\\d{10}") ? digits : null;
    }

    public static boolean isValid(String raw) {
        String digits = normalize(raw);
        if (digits == null) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (digits.charAt(i) - '0') * WEIGHTS[i];
        }
        sum += ((digits.charAt(8) - '0') * 5) / 10;
        int check = (10 - (sum % 10)) % 10;
        return check == digits.charAt(9) - '0';
    }
}
