package model;

public enum Severity {
    NORMAL, LOW, MEDIUM, HIGH, CRITICAL;

    public static Severity from_ratio(double ratio) {
        if (ratio <= 1.0) return NORMAL;
        if (ratio <= 1.5) return LOW;
        if (ratio <= 2.0) return MEDIUM;
        if (ratio <= 3.0) return HIGH;
        return CRITICAL;
    }
}
