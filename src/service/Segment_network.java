package service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
//this read the segment and connect the segment and perform bfs and dfs on segment
public class Segment_network {
    public static class Segment 
    {
        public final String id;
        public final String next;
        public final double longitude;
        public final double latitude;
        public final double length_km;

        public Segment(String id, String next, double longitude, double latitude, double length_km) {
            this.id = id;
            this.next = next;
            this.longitude = longitude;
            this.latitude = latitude;
            this.length_km = length_km;
        }
    }

    public static class Walk_result //result of bfs and dfs
    {
        public final List<String> order = new ArrayList<>();//order of walk
        public final Map<String, String> parent = new LinkedHashMap<>();//parent list
        public final List<String> station_ids = new ArrayList<>();//id discovered
        public final List<String[]> edges = new ArrayList<>();
    }

    public static class Route_result 
    {
        public final List<String> segments = new ArrayList<>();
        public final List<String[]> edges = new ArrayList<>();
        public boolean found;
        public boolean same_segment;
        public boolean same_station;
        public int hops;//connection between segments
    }

    private final Map<String, Segment> segment_map = new LinkedHashMap<>();
    private final Map<String, List<String>> upstream = new HashMap<>();
    private final Map<String, List<String>> downstream = new HashMap<>();
    private final Map<String, String> station_segments = new LinkedHashMap<>();
    private final List<Segment> segment_rows = new ArrayList<>();

    public Segment_network(Path folder) throws IOException {
        load_segments(folder.resolve("segments.csv"));
        load_station_mapping(folder.resolve("station_mapping.csv"));
        build_links();
    }
//load segment and assign them to hash map
    private void load_segments(Path file) throws IOException {
        List<String> rows = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i).isBlank()) continue;
            String[] data = rows.get(i).split(",", -1);
            if (data.length != 5) throw new IOException("Invalid segments.csv row " + (i + 1));
            Segment segment = new Segment(data[0].trim(), data[1].trim(), Double.parseDouble(data[2].trim()),
                    Double.parseDouble(data[3].trim()), Double.parseDouble(data[4].trim()));
            if (segment_map.putIfAbsent(segment.id, segment) != null) {
                throw new IOException("Duplicate river segment ID: " + segment.id);
            }
            segment_rows.add(segment);
            upstream.put(segment.id, new ArrayList<>());
            downstream.put(segment.id, new ArrayList<>());
        }
    }
//load station and assign them to object
    private void load_station_mapping(Path file) throws IOException {
        List<String> rows = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i).isBlank()) continue;
            String[] data = rows.get(i).split(",", -1);
            if (data.length < 4) throw new IOException("Invalid station_mapping.csv row " + (i + 1));
            String station_id = data[0].trim();
            String segment_id = data[3].trim();
            if (station_id.isEmpty() || segment_id.isEmpty() || !segment_map.containsKey(segment_id)) {
                throw new IOException("Invalid station-to-segment mapping on row " + (i + 1));
            }
            if (station_segments.putIfAbsent(station_id, segment_id) != null) {
                throw new IOException("Duplicate station mapping: " + station_id);
            }
        }
    }

    //assign upstream and dowsnstream
    private void build_links() throws IOException {
        for (Segment segment : segment_rows) {
            if (segment.next.isEmpty() || segment.next.equals("0")) continue;
            if (!segment_map.containsKey(segment.next)) {
                throw new IOException("Unknown downstream segment " + segment.next + " from " + segment.id);
            }
            downstream.get(segment.id).add(segment.next);
            upstream.get(segment.next).add(segment.id);
        }
    }

    public List<Segment> get_segments() {
        return Collections.unmodifiableList(segment_rows);
    }

    public String get_station_segment(String station_id) {
        return station_segments.get(station_id);
    }
//tracing of walk minimum path by bfs
    public Walk_result trace(String station_id, boolean trace_upstream, boolean depth_first) {
        String start = station_segments.get(station_id);
        if (start == null) throw new IllegalArgumentException("No river segment is mapped to station " + station_id);
        Walk_result result = new Walk_result();
        Deque<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        visited.add(start);
        result.parent.put(start, start);
        pending.addLast(start);

        while (!pending.isEmpty()) {
            String current = depth_first ? pending.removeLast() : pending.removeFirst();
            result.order.add(current);
            List<String> neighbours = trace_upstream ? upstream.get(current) : downstream.get(current);
            if (depth_first) {
                for (int i = neighbours.size() - 1; i >= 0; i--) {
                    String next = neighbours.get(i);
                    if (visited.add(next)) {
                        result.parent.put(next, current);
                        pending.addLast(next);
                    }
                }
            } else {
                for (String next : neighbours) {
                    if (visited.add(next)) {
                        result.parent.put(next, current);
                        pending.addLast(next);
                    }
                }
            }
        }

        for (int i = 1; i < result.order.size(); i++) {
            String segment_id = result.order.get(i);
            String parent_id = result.parent.get(segment_id);
            if (trace_upstream) result.edges.add(new String[]{segment_id, parent_id});
            else result.edges.add(new String[]{parent_id, segment_id});
        }

        for (String segment_id : result.order) {
            if (segment_id.equals(start)) continue;
            for (Map.Entry<String, String> mapping : station_segments.entrySet()) {
                if (mapping.getValue().equals(segment_id)) result.station_ids.add(mapping.getKey());
            }
        }
        return result;
    }

    public Route_result downstream_route(String start_station, String destination_station) {
        String start = station_segments.get(start_station);
        String destination = station_segments.get(destination_station);
        if (start == null) throw new IllegalArgumentException("No river segment is mapped to the start station.");
        if (destination == null) throw new IllegalArgumentException("No river segment is mapped to the destination station.");

        Route_result result = new Route_result();
        result.same_station = start_station.equals(destination_station);
        result.same_segment = start.equals(destination);

        Deque<String> pending = new ArrayDeque<>();
        Map<String, String> parent = new HashMap<>();
        Set<String> visited = new HashSet<>();
        pending.addLast(start);
        visited.add(start);
        parent.put(start, start);

        while (!pending.isEmpty() && !visited.contains(destination)) {
            String current = pending.removeFirst();
            for (String next : downstream.get(current)) {
                if (visited.add(next)) {
                    parent.put(next, current);
                    pending.addLast(next);
                }
            }
        }

        if (!visited.contains(destination)) return result;
        result.found = true;
        String current = destination;
        while (!current.equals(start)) {
            result.segments.add(current);
            current = parent.get(current);
        }
        result.segments.add(start);
        Collections.reverse(result.segments);
        for (int i = 1; i < result.segments.size(); i++) {
            result.edges.add(new String[]{result.segments.get(i - 1), result.segments.get(i)});
        }
        result.hops = result.segments.size() - 1;
        return result;
    }
}
