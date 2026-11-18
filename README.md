# RiverTrace

An Object Oriented River Pollution Source and Impact Tracing System in Java, updated with the genuine 2022 and 2023 Central Pollution Control Board (CPCB) National Water Quality Monitoring Programme (NWQMP) dataset and HydroRIVERS river network.

## Requirements

JDK 17 or newer, a browser, and an extracted copy of this folder. No Maven, external databases, Node.js, internet connection or third-party Java libraries are needed to compile and run the project.

## Windows Setup

1. Extract the ZIP into a directory of your choice.
2. Open a terminal in the extracted `RiverTrace` directory. Check `java -version` and `javac -version` (both must be 17 or newer).
3. Double-click `run.bat`, or run `.\run.bat` from PowerShell.
4. Keep the terminal open. Open http://localhost:8080 in your browser.
5. Press Ctrl+C in the terminal to stop the server.

If port 8080 is occupied, run `.\run.bat 8081` and open http://localhost:8081.

## Linux / macOS Setup

Run `sh run.sh`, then open http://localhost:8080 in your browser. Use `sh run.sh 8081` for an alternative port.

## Eclipse Setup

Create a Java Project with Java 17 or newer, turn off `module-info` creation, then import the contents of this folder into the project. Keep `src` as the source folder. Run `Main.java` as a Java Application. Set Run Configurations > Arguments > Working directory to the project folder containing `data` and `web`. Open http://localhost:8080 in a browser. Stop the previous run before starting again.

## Dataset and Network Integration

- **Dataset Source**: Official Central Pollution Control Board (CPCB), National Water Quality Monitoring Programme (NWQMP) River Data for 2022 and 2023.
- **Monitoring Stations**: 18 genuine CPCB monitoring stations along the Ganga, Bhagirathi, Alaknanda, Mandakini, Yamuna, Betwa, and Gomti rivers, using their official CPCB station identifiers (e.g., 1491 Gangotri, 1060 Rishikesh, 1067 Kanpur upstream, 1049 Prayagraj Sangam, 2486 Mirzapur, 1071 Varanasi downstream, 2283 Hamirpur, 1356 Betwa Hamirpur, 1353 Jaunpur Gomti, 10152 Bhusaula after Gomti confluence).
- **Parameters and Units**:
  - `BOD` (Biochemical Oxygen Demand): standard limit `3.0 mg/L` (CPCB Outdoor Bathing Class B/C; higher is worse).
  - `DO` (Dissolved Oxygen): standard limit `5.0 mg/L` minimum (CPCB Class B/C; lower is worse, minimum threshold).
  - `PH` (pH): acceptable range `6.5 to 8.5` (dimensionless; values outside this range represent violations).
  - `FC` (Faecal Coliform): standard limit `2500 MPN/100 mL` maximum permissible (CPCB Bathing Water Criteria; higher is worse).
- **Records and Granularity**:
  - The imported dataset contains 119 initial records across 2022 and 2023.
  - Annual summaries preserve exact minimum and maximum ranges (`min_value`, `max_value`) and retain their genuine reporting years (`2022`, `2023`) without inventing artificial dates or daily timestamps.
  - Missing parameters in the source data (such as BDL records for station 1491 and 1489 in 2022, or stations established in 2023) are preserved as unavailable (`No record` / `null`) rather than converted to zeros.
- **Network Mapping**:
  - The river topology connects all 18 stations following HydroRIVERS reach adjacency links, headwater tributaries, and natural confluences (Devprayag confluence C01, Hamirpur Betwa confluence C02, Prayagraj Sangam confluence C03, and Gomti confluence C04).
  - Upstream and downstream traversals are executed purely in Java using visited-set Breadth-First Search (BFS) and Depth-First Search (DFS).
- **OOP Reading Inheritance**:
  - `Reading`: abstract base class implementing `Comparable<Reading>`.
  - `Annual_reading`: concrete class encapsulating imported annual summary ranges (`year`, `min_value`, `max_value`, `source`).
  - `Sample_reading`: concrete class encapsulating individual dated samples (`LocalDateTime time`, `value`, `source`).
  - Both subclasses coexist seamlessly in station histories and CSV persistence.

## Features and Functionality

- **Overview Dashboard**: Stations needing attention based on threshold exceedance, highest-priority pollution events, active thresholds, and quick trace links.
- **Station Directory**: Search by name, ID, or region; filter by region and severity status; sort by name, risk/severity, or reporting period.
- **River Explorer**: A separate navigation page using the original HydroRIVERS river geometry and the station-to-segment mapping. Java performs upstream/downstream BFS and DFS over directed river-segment connections and returns traversal predecessors for segment highlighting. Playback supports Play, Pause, resume, next step, show all and reset trace. Map pan/zoom and Reset view are separate from trace reset. The page shows reached stations, flagged stations, statuses and missing readings for the selected parameter and historical cutoff.
- **Pollution Events**: PriorityQueue-ordered historical events based on severity and recency. Inspecting an event applies its historical cutoff, focuses the station, and opens reading history.
- **Repeated Exceedance Analysis**: Identifies stations exceeding thresholds across 2 or more reporting periods or distinct sample dates.
- **Critical Location Ranking**: Custom `Comparator<River_node>` ordering stations, confluences, and tributaries based on upstream maximum pollutant ratio multiplied by node risk weight (confluence 1.5, reservoir 1.3, tributary 1.1, station 1.0).
- **Reading History & Replay**: Step through available reporting periods and sample dates with `Previous` and `Next` buttons.
- **Add Reading**: Record new dated samples with validated non-negative numbers, date/time, and duplicate detection.
- **Edit Reading**: Modify existing records with stale-edit collision detection.
- **Delete Reading**: Remove records with explicit confirmation dialog and immediate recalculation.
- **Persistence Across Restarts**: All changes write atomically to `data/readings.csv`. Added records, edits, and deletions persist across application restarts.
- **CSV Export**: Export filtered history records directly to CSV from the browser.

## Walkthrough

1. Open Overview. Click one of the attention cards (e.g. Kanpur upstream - Shuklaganj) to focus the station and inspect all 4 parameter metrics (BOD, DO, pH, FC).
2. Open Stations. Filter by status `Exceeds threshold` or search `Kanpur`. Click `Select` to focus.
3. Open `River Explorer` from the navigation or click `Trace connections` in the Focus Station panel. The focused station is selected as the start. Choose `Upstream` or `Downstream`, and `BFS` or `DFS`. Use Play/Pause, Show next step or Show all to inspect the Java traversal. Use Reset trace to rewind it, or Reset view to restore map zoom and position.
4. Open Pollution & Analysis. Review the list of prioritized events. Click `Inspect` on a 2022 event: the interface applies `2022` as a cutoff and opens Reading History.
5. In Reading History, examine the 2022 annual summary. Click `Next` to step forward to the 2023 record. Click `Return to latest` in the filter bar when done.
6. Click `+ Add reading`. Select station `1067`, parameter `BOD`, value `5.8`, and a sample date/time. Click `Save reading`. The new sample appears as a `SAMPLE` entry alongside existing `ANNUAL` summaries.
7. Click `Edit` beside the sample to adjust its value, or click `Delete` to open the confirmation modal and remove it.
8. Filter by status or date range, then click `Export CSV` to download the filtered table.
9. Restart the application (`Ctrl+C` then `run.bat`) to verify that all modifications persist.

## Source Code Architecture

| Component | Files | Description |
| --- | --- | --- |
| **Model** | `src/model/River_node.java`, `Monitoring_station.java`, `Confluence.java`, `Tributary.java`, `Reservoir.java` | Node hierarchy, risk weights, polymorphism, and adjacency links. |
| **Readings** | `src/model/Reading.java`, `Annual_reading.java`, `Sample_reading.java`, `Pollutant.java`, `Severity.java`, `Pollution_event.java` | Polymorphic measurement model distinguishing annual summaries from dated samples, CPCB criteria, severity classification, and event priority comparison. |
| **Service** | `src/service/River_network.java`, `Tracer.java`, `Source_tracer.java`, `Impact_tracer.java`, `Trace_walk.java`, `Segment_network.java` | OOP river nodes and Java BFS/DFS over actual directed HydroRIVERS segment IDs, station mapping, predecessor edges and downstream BFS route finding. |
| **Analysis** | `src/service/Event_manager.java` | PriorityQueue event retrieval, recurring period exceedance detection, and cumulative upstream risk scoring. |
| **Storage & Util** | `src/util/Storage.java`, `Json.java`, `Invalid_reading.java`, `Station_not_found.java` | Local CSV persistence, atomic file writing, JSON serialization, and custom checked exceptions. |
| **Server** | `src/Main.java` | Java HTTP server, REST API endpoints (`/api/state`, `/api/network`, `/api/trace`, `/api/route`, `/api/readings`, `/api/update`, `/api/delete`), static web serving, and request validation. |
| **Frontend** | `web/index.html`, `web/app.js`, `web/style.css` | Accessible vanilla HTML/CSS/JavaScript interface. River Explorer renders original GeoJSON reaches and animates Java traversal results. |
| **Data** | `data/nodes.csv`, `links.csv`, `readings.csv`, `records.csv`, `stations.csv`, `rivers.csv`, `segments.csv`, `station_mapping.csv`, `network.geojson`, `licenses/` | CPCB NWQMP monitoring data, HydroRIVERS reach geometries and topologies, and source license attributions. |
