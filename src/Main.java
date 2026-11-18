import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import model.*;
import service.*;
import util.*;

public class Main {
    private static River_network network;
    private static Storage storage;
    private static Segment_network segment_network;
    private static Event_manager manager = new Event_manager();

    public static void main(String[] args) throws Exception {
        int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
        storage = new Storage(Path.of("data"));
        network = storage.load();
        segment_network = new Segment_network(Path.of("data"));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", Main::handle);
        server.start();
        System.out.println("RiverTrace is running at http://localhost:" + port);
        System.out.println("Keep this window open. Press Ctrl+C to stop.");
    }

    private static void handle(HttpExchange request) throws IOException {
        try {
            String path = request.getRequestURI().getPath();
            if (request.getRequestMethod().equals("GET") && path.startsWith("/api/")) {
                Map<String, String> query = parse(request.getRequestURI().getRawQuery());
                if (path.equals("/api/state")) send(request, 200, state(query), "application/json");
                else if (path.equals("/api/network")) send(request, 200, network_json(query), "application/json");
                else if (path.equals("/api/trace")) send(request, 200, trace_json(query), "application/json");
                else if (path.equals("/api/route")) send(request, 200, route_json(query), "application/json");
                else send(request, 404, "{\"error\":\"API route not found.\"}", "application/json");
            } else if ((path.equals("/api/readings") || path.equals("/api/update") || path.equals("/api/delete"))
                    && request.getRequestMethod().equals("POST")) {
                String origin = request.getRequestHeaders().getFirst("Origin");
                String host = request.getRequestHeaders().getFirst("Host");
                if (origin != null && !origin.equals("http://" + host)) {
                    send(request, 403, "{\"error\":\"Request origin is not allowed.\"}", "application/json");
                    return;
                }
                byte[] body = request.getRequestBody().readNBytes(8193);
                if (body.length > 8192) {
                    throw new IllegalArgumentException("Request is too large.");
                }
                Map<String, String> form = parse(new String(body, StandardCharsets.UTF_8));
                change_reading(path, form);
                send(request, path.equals("/api/readings") ? 201 : 200,
                        "{\"message\":\"Reading " + (path.equals("/api/delete") ? "deleted" : "saved") + ".\"}", "application/json");
            } else if (request.getRequestMethod().equals("GET") && path.equals("/network.geojson")) {
                send(request, 200, Files.readString(Path.of("data", "network.geojson")), "application/geo+json");
            } else if (request.getRequestMethod().equals("GET") &&
                    (path.equals("/") || path.equals("/style.css") || path.equals("/app.js"))) {
                String file = path.equals("/") ? "index.html" : path.substring(1);
                String type = file.endsWith("html") ? "text/html" : file.endsWith("css") ? "text/css" : "text/javascript";
                send(request, 200, Files.readString(Path.of("web", file)), type);
            } else {
                send(request, 404, "{\"error\":\"Page or method not found.\"}", "application/json");
            }
        } catch (Station_not_found error) {
            send(request, 404, "{\"error\":" + Json.text(error.getMessage()) + "}", "application/json");
        } catch (IllegalArgumentException | Invalid_reading | java.time.format.DateTimeParseException error) {
            send(request, 400, "{\"error\":" + Json.text("Invalid input: " + error.getMessage()) + "}", "application/json");
        } catch (Exception error) {
            System.err.println(error.getMessage());
            send(request, 500, "{\"error\":\"Could not complete the request. Check the terminal and data files.\"}", "application/json");
        } finally {
            request.close();
        }
    }

    private static void change_reading(String path, Map<String, String> form) throws Exception {
        Monitoring_station station = network.find_station(required(form, "station"));
        Pollutant pollutant = Pollutant.valueOf(required(form, "pollutant"));
        Reading old = null;
        if (!path.equals("/api/readings")) {
            String old_time = required(form, "old_time");
            for (Reading reading : station.get_readings()) {
                if (reading.get_pollutant() == pollutant && reading.get_time_label().equals(old_time)) {
                    old = reading;
                }
            }
            if (old == null) {
                throw new IllegalArgumentException("That reading no longer exists. Refresh the page.");
            }
            double old_value = Double.parseDouble(required(form, "old_value"));
            if (Double.compare(old.get_value(), old_value) != 0) {
                throw new IllegalArgumentException("That reading has changed. Refresh before editing it.");
            }
        }
        Reading added = null;
        if (!path.equals("/api/delete")) {
            double value = Double.parseDouble(required(form, "value"));
            String time_str = required(form, "time");
            if (time_str.length() == 4) {
                added = new Annual_reading(pollutant, Integer.parseInt(time_str), value, value, "MANUAL");
            } else {
                added = new Sample_reading(pollutant, value, LocalDateTime.parse(time_str), "MANUAL");
            }
            for (Reading reading : station.get_readings()) {
                if (reading != old && reading.get_pollutant() == pollutant && reading.get_time_label().equals(added.get_time_label())) {
                    throw new IllegalArgumentException("This station already has that pollutant at that time.");
                }
            }
        }
        storage.save_change(network, station, old, added);
        if (old != null) {
            station.remove_reading(old);
        }
        if (added != null) {
            station.add_reading(added);
        }
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return value;
    }

    private static Map<String, String> parse(String text) {
        Map<String, String> result = new HashMap<>();
        if (text == null || text.isEmpty()) {
            return result;
        }
        for (String part : text.split("&")) {
            String[] pair = part.split("=", 2);
            result.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "");
        }
        return result;
    }

    private static String state(Map<String, String> query) throws Exception {
        Pollutant pollutant = selected_pollutant(query);
        String until_param = query.getOrDefault("until", "").trim();
        LocalDateTime until = selected_until(until_param);

        List<Monitoring_station> all_stations = network.get_stations();
        if (all_stations.isEmpty()) {
            throw new IllegalArgumentException("No monitoring stations available.");
        }
        String default_station_id = all_stations.get(0).get_id();
        for (Monitoring_station st : all_stations) {
            if (st.get_id().equals("1067")) {
                default_station_id = "1067";
                break;
            }
        }
        Monitoring_station selected = network.find_station(query.getOrDefault("station", default_station_id));

        List<String> stations = new ArrayList<>();
        List<String> recurring = new ArrayList<>();
        int abnormal = 0;
        int count = 0;
        for (Monitoring_station station : all_stations) {
            Reading latest = manager.latest(station, pollutant, until);
            if (latest != null && latest.get_severity() != Severity.NORMAL) {
                abnormal++;
            }
            int periods = manager.recurring_periods(station, pollutant, until);
            String row = node_json(station, pollutant, until);
            stations.add(row);
            if (periods >= 2) {
                recurring.add("{\"id\":" + Json.text(station.get_id()) + ",\"name\":" + Json.text(station.get_name())
                        + ",\"days\":" + periods + "}");
            }
            for (Reading reading : station.get_readings()) {
                if (reading.get_pollutant() == pollutant && manager.is_valid_until(reading, until)) {
                    count++;
                }
            }
        }

        List<String> events = new ArrayList<>();
        for (Pollution_event event : manager.get_events(network, until)) {
            if (event.get_reading().get_pollutant() == pollutant) {
                events.add("{\"station\":" + Json.text(event.get_station().get_name()) + ",\"id\":"
                        + Json.text(event.get_station().get_id()) + ",\"reading\":" + reading_json(event.get_reading()) + "}");
            }
        }

        List<String> history = new ArrayList<>();
        for (Reading reading : selected.get_readings()) {
            if (reading.get_pollutant() == pollutant && manager.is_valid_until(reading, until)) {
                history.add(reading_json(reading));
            }
        }

        List<River_node> ranked = network.get_nodes();
        Collections.sort(ranked, (first, second) ->
                Double.compare(manager.risk_score(second, pollutant, until), manager.risk_score(first, pollutant, until)));
        List<String> critical = new ArrayList<>();
        for (River_node node : ranked) {
            double score = manager.risk_score(node, pollutant, until);
            if (score > 1.0) {
                critical.add("{\"id\":" + Json.text(node.get_id()) + ",\"name\":" + Json.text(node.get_name())
                        + ",\"type\":" + Json.text(node.describe()) + ",\"score\":" + score + "}");
            }
        }

        List<String> times = new ArrayList<>();
        Set<String> timeline = new LinkedHashSet<>();
        for (Reading reading : selected.get_readings()) {
            if (reading.get_pollutant() == pollutant) {
                timeline.add(reading.get_time_label());
            }
        }
        for (String time_item : timeline) {
            times.add(Json.text(time_item));
        }

        List<String> metrics = new ArrayList<>();
        for (Pollutant item : Pollutant.values()) {
            metrics.add("{\"pollutant\":" + Json.text(item.name())
                    + ",\"limit\":" + item.get_limit()
                    + ",\"unit\":" + Json.text(item.get_unit())
                    + ",\"rule\":" + Json.text(item.get_rule())
                    + ",\"reading\":" + reading_json(manager.latest(selected, item, until)) + "}");
        }

        return "{\"metrics\":" + Json.array(metrics)
                + ",\"stations\":" + Json.array(stations)
                + ",\"events\":" + Json.array(events)
                + ",\"history\":" + Json.array(history)
                + ",\"recurring\":" + Json.array(recurring)
                + ",\"critical\":" + Json.array(critical)
                + ",\"times\":" + Json.array(times)
                + ",\"abnormal\":" + abnormal
                + ",\"count\":" + count
                + ",\"nodes\":" + network.get_nodes().size()
                + ",\"limit\":" + pollutant.get_limit()
                + ",\"unit\":" + Json.text(pollutant.get_unit())
                + ",\"rule\":" + Json.text(pollutant.get_rule())
                + ",\"until\":" + Json.text(until_param.isEmpty() ? "Latest records" : until_param) + "}";
    }


    private static Pollutant selected_pollutant(Map<String, String> query) {
        return Pollutant.valueOf(query.getOrDefault("pollutant", "BOD"));
    }

    private static LocalDateTime selected_until(String until_param) {
        LocalDateTime until;
        if (until_param == null || until_param.isBlank()) {
            until = LocalDateTime.now();
        } else if (until_param.length() == 4) {
            until = LocalDateTime.of(Integer.parseInt(until_param), 12, 31, 23, 59, 59);
        } else {
            until = LocalDateTime.parse(until_param);
        }
        if (until.isAfter(LocalDateTime.now().plusDays(1))) {
            throw new IllegalArgumentException("Replay time cannot be in the future.");
        }
        return until;
    }

    private static String network_json(Map<String, String> query) throws Exception {
        Pollutant pollutant = selected_pollutant(query);
        LocalDateTime until = selected_until(query.getOrDefault("until", "").trim());
        List<String> segments = new ArrayList<>();
        for (Segment_network.Segment segment : segment_network.get_segments()) {
            segments.add("{\"id\":" + Json.text(segment.id)
                    + ",\"next\":" + Json.text(segment.next)
                    + ",\"longitude\":" + segment.longitude
                    + ",\"latitude\":" + segment.latitude
                    + ",\"length_km\":" + segment.length_km + "}");
        }
        List<String> stations = new ArrayList<>();
        for (Monitoring_station station : network.get_stations()) {
            String segment_id = segment_network.get_station_segment(station.get_id());
            Reading reading = manager.latest(station, pollutant, until);
            String status = reading == null ? "NO DATA" : reading.get_severity().name();
            stations.add("{\"id\":" + Json.text(station.get_id())
                    + ",\"name\":" + Json.text(station.get_name())
                    + ",\"region\":" + Json.text(station.get_region())
                    + ",\"segment_id\":" + Json.text(segment_id)
                    + ",\"status\":" + Json.text(status)
                    + ",\"reading\":" + reading_json(reading) + "}");
        }
        return "{\"segments\":" + Json.array(segments)
                + ",\"stations\":" + Json.array(stations)
                + ",\"pollutant\":" + Json.text(pollutant.name())
                + ",\"until\":" + Json.text(query.getOrDefault("until", "")) + "}";
    }

    private static String trace_json(Map<String, String> query) throws Exception {
        String station_id = required(query, "station");
        String direction = query.getOrDefault("direction", "upstream");
        String algorithm = query.getOrDefault("algorithm", "BFS").toUpperCase(Locale.ROOT);
        if (!direction.equals("upstream") && !direction.equals("downstream")) {
            throw new IllegalArgumentException("Choose upstream or downstream.");
        }
        if (!algorithm.equals("BFS") && !algorithm.equals("DFS")) {
            throw new IllegalArgumentException("Choose BFS or DFS.");
        }
        Pollutant pollutant = selected_pollutant(query);
        LocalDateTime until = selected_until(query.getOrDefault("until", "").trim());
        network.find_station(station_id);
        Segment_network.Walk_result result = segment_network.trace(station_id, direction.equals("upstream"), algorithm.equals("DFS"));

        List<String> segments = new ArrayList<>();
        for (String id : result.order) {
            String parent_id = result.parent.get(id);
            segments.add("{\"id\":" + Json.text(id)
                    + ",\"parent\":" + (id.equals(parent_id) ? "null" : Json.text(parent_id)) + "}");
        }
        List<String> edges = new ArrayList<>();
        for (String[] edge : result.edges) {
            edges.add("{\"from\":" + Json.text(edge[0]) + ",\"to\":" + Json.text(edge[1]) + "}");
        }
        List<String> stations = new ArrayList<>();
        List<String> flagged = new ArrayList<>();
        for (String id : result.station_ids) {
            Monitoring_station station = network.find_station(id);
            Reading reading = manager.latest(station, pollutant, until);
            String row = station_trace_json(station, segment_network.get_station_segment(id), reading);
            stations.add(row);
            if (reading != null && reading.get_severity() != Severity.NORMAL) flagged.add(row);
        }
        return "{\"start\":" + Json.text(station_id)
                + ",\"direction\":" + Json.text(direction)
                + ",\"algorithm\":" + Json.text(algorithm)
                + ",\"segment_count\":" + result.order.size()
                + ",\"station_count\":" + stations.size()
                + ",\"flagged_count\":" + flagged.size()
                + ",\"segments\":" + Json.array(segments)
                + ",\"edges\":" + Json.array(edges)
                + ",\"stations\":" + Json.array(stations)
                + ",\"flagged\":" + Json.array(flagged) + "}";
    }

    private static String route_json(Map<String, String> query) throws Exception {
        String start_id = required(query, "start");
        String destination_id = required(query, "destination");
        Pollutant pollutant = selected_pollutant(query);
        LocalDateTime until = selected_until(query.getOrDefault("until", "").trim());
        network.find_station(start_id);
        network.find_station(destination_id);
        Segment_network.Route_result result = segment_network.downstream_route(start_id, destination_id);
        List<String> segments = new ArrayList<>();
        for (String id : result.segments) segments.add(Json.text(id));
        List<String> edges = new ArrayList<>();
        for (String[] edge : result.edges) {
            edges.add("{\"from\":" + Json.text(edge[0]) + ",\"to\":" + Json.text(edge[1]) + "}");
        }
        Monitoring_station start_station = network.find_station(start_id);
        Monitoring_station destination_station = network.find_station(destination_id);
        return "{\"found\":" + result.found
                + ",\"start\":" + Json.text(start_id)
                + ",\"destination\":" + Json.text(destination_id)
                + ",\"same_station\":" + result.same_station
                + ",\"same_segment\":" + result.same_segment
                + ",\"hops\":" + result.hops
                + ",\"segments\":" + Json.array(segments)
                + ",\"edges\":" + Json.array(edges)
                + ",\"start_name\":" + Json.text(start_station.get_name())
                + ",\"destination_name\":" + Json.text(destination_station.get_name())
                + ",\"pollutant\":" + Json.text(pollutant.name())
                + ",\"until\":" + Json.text(query.getOrDefault("until", "")) + "}";
    }

    private static String station_trace_json(Monitoring_station station, String segment_id, Reading reading) {
        return "{\"id\":" + Json.text(station.get_id())
                + ",\"name\":" + Json.text(station.get_name())
                + ",\"region\":" + Json.text(station.get_region())
                + ",\"segment_id\":" + Json.text(segment_id)
                + ",\"status\":" + Json.text(reading == null ? "NO DATA" : reading.get_severity().name())
                + ",\"reading\":" + reading_json(reading) + "}";
    }

    private static String node_json(River_node node, Pollutant pollutant, LocalDateTime until) {
        Reading latest = null;
        if (node instanceof Monitoring_station) {
            latest = manager.latest((Monitoring_station) node, pollutant, until);
        }
        return "{\"id\":" + Json.text(node.get_id()) + ",\"name\":" + Json.text(node.get_name())
                + ",\"region\":" + Json.text(node.get_region()) + ",\"type\":" + Json.text(node.describe())
                + ",\"reading\":" + reading_json(latest) + "}";
    }

    private static String reading_json(Reading reading) {
        if (reading == null) {
            return "null";
        }
        return "{\"pollutant\":" + Json.text(reading.get_pollutant().name())
                + ",\"value\":" + reading.get_value()
                + ",\"min\":" + reading.get_min_value()
                + ",\"max\":" + reading.get_max_value()
                + ",\"time\":" + Json.text(reading.get_time_label())
                + ",\"is_annual\":" + reading.is_annual()
                + ",\"source\":" + Json.text(reading.get_source())
                + ",\"unit\":" + Json.text(reading.get_pollutant().get_unit())
                + ",\"rule\":" + Json.text(reading.get_pollutant().get_rule())
                + ",\"severity\":" + Json.text(reading.get_severity().name()) + "}";
    }

    private static void send(HttpExchange request, int code, String text, String type) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        request.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        request.getResponseHeaders().set("Cache-Control", "no-store");
        request.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        request.sendResponseHeaders(code, data.length);
        request.getResponseBody().write(data);
    }
}
