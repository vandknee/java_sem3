package util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import model.*;
import service.River_network;

public class Storage {
    private Path folder;

    public Storage(Path folder) {
        this.folder = folder;
    }

    public River_network load() throws Exception {
        River_network network = new River_network();
        List<String> rows = Files.readAllLines(folder.resolve("nodes.csv"), StandardCharsets.UTF_8);
        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i).isBlank()) {
                continue;
            }
            String[] data = rows.get(i).split(",", -1);
            if (data.length != 4) {
                throw new IOException("Invalid nodes.csv row " + (i + 1));
            }
            River_node node;
            switch (data[1].trim()) {
                case "station":
                    node = new Monitoring_station(data[0].trim(), data[2].trim(), data[3].trim());
                    break;
                case "tributary":
                    node = new Tributary(data[0].trim(), data[2].trim(), data[3].trim());
                    break;
                case "reservoir":
                    node = new Reservoir(data[0].trim(), data[2].trim(), data[3].trim());
                    break;
                case "confluence":
                    node = new Confluence(data[0].trim(), data[2].trim(), data[3].trim());
                    break;
                default:
                    throw new IOException("Unknown node type in row " + (i + 1));
            }
            network.add_node(node);
        }

        rows = Files.readAllLines(folder.resolve("links.csv"), StandardCharsets.UTF_8);
        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i).isBlank()) {
                continue;
            }
            String[] data = rows.get(i).split(",", -1);
            if (data.length != 2) {
                throw new IOException("Invalid links.csv row " + (i + 1));
            }
            network.add_link(data[0].trim(), data[1].trim());
        }

        Path readings_file = folder.resolve("readings.csv");
        if (Files.exists(readings_file)) {
            rows = Files.readAllLines(readings_file, StandardCharsets.UTF_8);
            for (int i = 1; i < rows.size(); i++) {
                if (rows.get(i).isBlank()) {
                    continue;
                }
                String[] data = rows.get(i).split(",", -1);
                Monitoring_station station = network.find_station(data[0].trim());
                Pollutant pollutant = Pollutant.valueOf(data[1].trim());
                if (data.length >= 8) {
                    boolean is_annual = Boolean.parseBoolean(data[6].trim());
                    if (is_annual) {
                        int year = Integer.parseInt(data[3].trim());
                        double min_val = Double.parseDouble(data[4].trim());
                        double max_val = Double.parseDouble(data[5].trim());
                        String source = data[7].trim();
                        station.add_reading(new Annual_reading(pollutant, year, min_val, max_val, source));
                    } else {
                        double val = Double.parseDouble(data[2].trim());
                        LocalDateTime dt = LocalDateTime.parse(data[3].trim());
                        String source = data[7].trim();
                        station.add_reading(new Sample_reading(pollutant, val, dt, source));
                    }
                } else if (data.length >= 4) {
                    double val = Double.parseDouble(data[2].trim());
                    String time_str = data[3].trim();
                    if (time_str.length() == 4) {
                        station.add_reading(new Annual_reading(pollutant, Integer.parseInt(time_str), val, val, "IMPORT"));
                    } else {
                        station.add_reading(new Sample_reading(pollutant, val, LocalDateTime.parse(time_str), "MANUAL"));
                    }
                }
            }
        }
        return network;
    }

    public void save_change(River_network network, Monitoring_station target, Reading old, Reading added) throws IOException {
        List<String> rows = new ArrayList<>();
        rows.add("station,pollutant,value,time,min_value,max_value,is_annual,source");
        for (Monitoring_station station : network.get_stations()) {
            for (Reading reading : station.get_readings()) {
                if (reading != old) {
                    rows.add(row(station, reading));
                }
            }
        }
        if (added != null) {
            rows.add(row(target, added));
        }
        Path temporary = Files.createTempFile(folder, "readings-", ".tmp");
        try {
            Files.write(temporary, rows, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, folder.resolve("readings.csv"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, folder.resolve("readings.csv"), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private String row(Monitoring_station station, Reading reading) {
        return station.get_id() + "," + reading.get_pollutant().name() + "," + reading.get_value() + ","
                + reading.get_time_label() + "," + reading.get_min_value() + "," + reading.get_max_value() + ","
                + reading.is_annual() + "," + reading.get_source();
    }
}
