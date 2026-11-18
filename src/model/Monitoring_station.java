package model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import util.Invalid_reading;

public class Monitoring_station extends River_node {
    private LinkedList<Reading> readings = new LinkedList<>();

    public Monitoring_station(String id, String name, String region) {
        super(id, name, region);
    }

    public void add_reading(Reading reading) {
        readings.add(reading);
        Collections.sort(readings, Comparator.comparing(Reading::get_effective_time));
    }

    public void add_reading(Pollutant pollutant, double value, LocalDateTime time) throws Invalid_reading {
        add_reading(new Sample_reading(pollutant, value, time));
    }

    public void add_reading(Pollutant pollutant, int year, double min_value, double max_value, String source) throws Invalid_reading {
        add_reading(new Annual_reading(pollutant, year, min_value, max_value, source));
    }

    public void remove_reading(Reading reading) {
        readings.remove(reading);
    }

    public List<Reading> get_readings() {
        return Collections.unmodifiableList(readings);
    }

    @Override
    public String describe() {
        return "Monitoring station";
    }

    @Override
    public double get_risk_weight() {
        return 1.0;
    }
}
