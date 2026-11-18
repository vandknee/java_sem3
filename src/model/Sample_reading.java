package model;

import java.time.LocalDateTime;
import util.Invalid_reading;

public class Sample_reading extends Reading {
    private final double value;
    private final LocalDateTime time;
    private final String source;

    public Sample_reading(Pollutant pollutant, double value, LocalDateTime time) throws Invalid_reading {
        this(pollutant, value, time, "MANUAL");
    }

    public Sample_reading(Pollutant pollutant, double value, LocalDateTime time, String source) throws Invalid_reading {
        super(pollutant);
        if (time == null || !Double.isFinite(value) || value < 0) {
            throw new Invalid_reading("Enter a valid date and finite non-negative value.");
        }
        if (pollutant == Pollutant.PH && (value < 0 || value > 14)) {
            throw new Invalid_reading("pH value must be between 0 and 14.");
        }
        if (time.isAfter(LocalDateTime.now())) {
            throw new Invalid_reading("A reading cannot be dated in the future.");
        }
        this.value = value;
        this.time = time;
        this.source = source == null || source.isBlank() ? "MANUAL" : source;
    }

    @Override
    public double get_value() {
        return value;
    }

    @Override
    public double get_min_value() {
        return value;
    }

    @Override
    public double get_max_value() {
        return value;
    }

    @Override
    public String get_time_label() {
        return time.toString();
    }

    public LocalDateTime get_time() {
        return time;
    }

    @Override
    public LocalDateTime get_effective_time() {
        return time;
    }

    @Override
    public boolean is_annual() {
        return false;
    }

    @Override
    public String get_source() {
        return source;
    }

    @Override
    public double get_ratio() {
        if (pollutant == Pollutant.BOD) {
            return value / 3.0;
        }
        if (pollutant == Pollutant.DO) {
            if (value >= 5.0) {
                return 1.0;
            }
            return 5.0 / Math.max(0.1, value);
        }
        if (pollutant == Pollutant.PH) {
            double dev = Math.max(0.0, 6.5 - value) + Math.max(0.0, value - 8.5);
            return dev <= 0.0 ? 1.0 : 1.0 + dev;
        }
        if (pollutant == Pollutant.FC) {
            return value / 2500.0;
        }
        return value / pollutant.get_limit();
    }

    @Override
    public Severity get_severity() {
        if (pollutant == Pollutant.BOD) {
            return Severity.from_ratio(get_ratio());
        }
        if (pollutant == Pollutant.DO) {
            if (value >= 5.0) {
                return Severity.NORMAL;
            }
            if (value >= 4.0) {
                return Severity.LOW;
            }
            if (value >= 3.0) {
                return Severity.MEDIUM;
            }
            if (value >= 2.0) {
                return Severity.HIGH;
            }
            return Severity.CRITICAL;
        }
        if (pollutant == Pollutant.PH) {
            double dev = Math.max(0.0, 6.5 - value) + Math.max(0.0, value - 8.5);
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
