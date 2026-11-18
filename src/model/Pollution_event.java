package model;

public class Pollution_event implements Comparable<Pollution_event> {
    private Monitoring_station station;
    private Reading reading;

    public Pollution_event(Monitoring_station station, Reading reading) {
        this.station = station;
        this.reading = reading;
    }

    public Monitoring_station get_station() {
        return station;
    }

    public Reading get_reading() {
        return reading;
    }

    @Override
    public int compareTo(Pollution_event other) {
        int order = other.reading.get_severity().compareTo(reading.get_severity());
        if (order == 0) {
            order = other.reading.get_effective_time().compareTo(reading.get_effective_time());
        }
        if (order == 0) {
            order = station.get_id().compareTo(other.station.get_id());
        }
        return order;
    }
}
