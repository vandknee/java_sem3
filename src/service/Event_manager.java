package service;

import java.time.LocalDateTime;
import java.util.*;
import model.*;

public class Event_manager {
    public boolean is_valid_until(Reading reading, LocalDateTime until) {
        if (reading == null || until == null) {
            return false;
        }
        if (reading.is_annual()) {
            return until.getYear() >= ((Annual_reading) reading).get_year();
        }
        return !reading.get_effective_time().isAfter(until);
    }

    public List<Pollution_event> get_events(River_network network, LocalDateTime until) {
        PriorityQueue<Pollution_event> events = new PriorityQueue<>();
        for (Monitoring_station station : network.get_stations()) {
            for (Reading reading : station.get_readings()) {
                if (is_valid_until(reading, until) && reading.get_severity() != Severity.NORMAL) {
                    events.add(new Pollution_event(station, reading));
                }
            }
        }
        List<Pollution_event> result = new ArrayList<>();
        while (!events.isEmpty()) {
            result.add(events.remove());
        }
        return result;
    }

    public Reading latest(Monitoring_station station, Pollutant pollutant, LocalDateTime until) {
        Reading result = null;
        for (Reading reading : station.get_readings()) {
            if (reading.get_pollutant() == pollutant && is_valid_until(reading, until)) {
                result = reading;
            }
        }
        return result;
    }

    public int recurring_periods(Monitoring_station station, Pollutant pollutant, LocalDateTime until) {
        Set<String> periods = new HashSet<>();
        for (Reading reading : station.get_readings()) {
            if (reading.get_pollutant() == pollutant && is_valid_until(reading, until) && reading.get_severity() != Severity.NORMAL) {
                if (reading.is_annual()) {
                    periods.add(reading.get_time_label());
                } else {
                    periods.add(reading.get_effective_time().toLocalDate().toString());
                }
            }
        }
        return periods.size();
    }

    public double risk_score(River_node node, Pollutant pollutant, LocalDateTime until) {
        double ratio = 0.0;
        List<River_node> contributors = new Source_tracer().trace(node);
        contributors.add(node);
        for (River_node source : contributors) {
            if (source instanceof Monitoring_station) {
                Reading reading = latest((Monitoring_station) source, pollutant, until);
                if (reading != null) {
                    ratio = Math.max(ratio, reading.get_ratio());
                }
            }
        }
        return ratio * node.get_risk_weight();
    }
}
