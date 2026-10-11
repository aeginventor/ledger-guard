package io.github.aeginventor.ledgerguard.voucher;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DrafterType {
    FIXTURE("fixture"), OPENAI("openai"), MANUAL("manual");

    private final String label;

    DrafterType(String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }

    @JsonCreator
    public static DrafterType fromLabel(String value) {
        for (DrafterType type : values()) {
            if (type.label.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("unknown drafter: " + value);
    }
}
