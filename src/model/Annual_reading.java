package model;

import java.time.LocalDateTime;
import util.Invalid_reading;

public class Annual_reading extends Reading {
    private final int year;
    private final double min_value;
    private final double max_value;
    private final double value;
    private final String source;

    public Annual_reading(Pollutant pollutant, int year, double min_value, double max_value, String source) throws Invalid_reading {
        super(pollutant);
        if (year < 1900 || year > 2100) {
            throw new Invalid_reading("Invalid year: " + year);
        }
        if (!Double.isFinite(min_value) || !Double.isFinite(max_value) || min_value < 0 || max_value < 0) {
            throw new Invalid_reading("Values must be finite and non-negative.");
        }
        if (min_value > max_value) {
            throw new Invalid_reading("Minimum value cannot exceed maximum value.");
        }
        this.year = year;
        this.min_value = min_value;
        this.max_value = max_value;
        this.source = source == null || source.isBlank() ? "CPCB " + year : source;
        if (pollutant == Pollutant.DO) {
            this.value = min_value;
        } else if (pollutant == Pollutant.PH) {
            double dev_low = Math.max(0.0, 6.5 - min_value);
            double dev_high = Math.max(0.0, max_value - 8.5);
            this.value = dev_high >= dev_low ? max_value : min_value;
        } else {
            this.value = max_value;
        }
    }

    @Override
    public double get_value() {
        return value;
    }

    @Override
    public double get_min_value() {
        return min_value;
    }

    @Override
    public double get_max_value() {
        return max_value;
    }

    @Override
    public String get_time_label() {
        return String.valueOf(year);
    }

    public int get_year() {
        return year;
    }

    @Override
    public LocalDateTime get_effective_time() {
        return LocalDateTime.of(year, 12, 31, 23, 59, 59);
    }

    @Override
    public boolean is_annual() {
        return true;
    }

    @Override
    public String get_source() {
        return source;
    }

    @Override
    public double get_ratio() {
        if (pollutant == Pollutant.BOD) {
            return max_value / 3.0;
        }
        if (pollutant == Pollutant.DO) {
            if (min_value >= 5.0) {
                return 1.0;
            }
            return 5.0 / Math.max(0.1, min_value);
        }
        if (pollutant == Pollutant.PH) {
            double dev = Math.max(0.0, 6.5 - min_value) + Math.max(0.0, max_value - 8.5);
            return dev <= 0.0 ? 1.0 : 1.0 + dev;
        }
        if (pollutant == Pollutant.FC) {
            return max_value / 2500.0;
        }
        return value / pollutant.get_limit();
    }

    @Override
    public Severity get_severity() {
        if (pollutant == Pollutant.BOD) {
            return Severity.from_ratio(get_ratio());
        }
        if (pollutant == Pollutant.DO) {
            if (min_value >= 5.0) {
                return Severity.NORMAL;
            }
            if (min_value >= 4.0) {
                return Severity.LOW;
            }
            if (min_value >= 3.0) {
                return Severity.MEDIUM;
            }
            if (min_value >= 2.0) {
                return Severity.HIGH;
            }
            return Severity.CRITICAL;
        }
        if (pollutant == Pollutant.PH) {
            double dev = Math.max(0.0, 6.5 - min_value) + Math.max(0.0, max_value - 8.5);
            if (dev <= 0.0) {
                return Severity.NORMAL;
            }
            if (dev <= 0.25) {
                return Severity.LOW;
            }
            if (dev <= 0.6) {
                return Severity.MEDIUM;
            }
            if (dev <= 1.2) {
                return Severity.HIGH;
            }
            return Severity.CRITICAL;
        }
        if (pollutant == Pollutant.FC) {
            return Severity.from_ratio(get_ratio());
        }
        return Severity.from_ratio(get_ratio());
    }
}
