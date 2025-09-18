package org.nuxeo.extended.utils;

public enum Operator {
    EQUALS("="),
    NOT_EQUALS("!="),
    LESS_THAN("<"),
    GREATER_THAN(">"),
    IN("IN"),
    NOT_IN("NOT IN"),
    BETWEEN("BETWEEN"),
    LESS_THAN_OR_EQUAL("<="),
    GREATER_THAN_OR_EQUAL(">="),
    LIKE("LIKE"),
    ILIKE("ILIKE"),
    NOT_LIKE("NOT LIKE"),
    NOT_ILIKE("NOT ILIKE"),
    STARTS_WITH("STARTSWITH"),
    FULLTEXT("FULLTEXT");

    private final String value;

    Operator(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static boolean isValidOperator(String operator) {
        for (Operator op : Operator.values()) {
            if (op.getValue().equalsIgnoreCase(operator)) {
                return true;
            }
        }
        return false;
    }
}
