package model;

public enum Pollutant {
    BOD(3.0, "mg/L", "Max <= 3.0 mg/L"),
    DO(5.0, "mg/L", "Min >= 5.0 mg/L"),
    PH(8.5, "pH", "Range 6.5 - 8.5"),
    FC(2500.0, "MPN/100 mL", "Max <= 2500 MPN/100 mL");

    private final double limit;
    private final String unit;
    private final String rule;

    Pollutant(double limit, String unit, String rule) {
        this.limit = limit;
        this.unit = unit;
        this.rule = rule;
    }

    public double get_limit() { return limit; }
    public String get_unit() { return unit; }
    public String get_rule() { return rule; }
}
