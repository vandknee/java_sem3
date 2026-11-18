let state = null;
let settings = {station: '1067', pollutant: 'BOD', until: ''};
let active_view = 'overview';
let request_number = 0;
let river_geometry = null;
let river_network = null;
let trace_data = null;
let route_data = null;
let trace_revealed = 0;
let trace_timer = null;
let trace_request = 0;
let map_view = {scale: 1, x: 0, y: 0};
let map_drag = null;
let map_bounds = null;
let editing = null;
let deleting = null;
let saving = false;

const find = id => document.getElementById(id);
const names = {overview: 'Overview', stations: 'Stations', events: 'Pollution & analysis', history: 'Reading history', river_explorer: 'River Explorer'};
const titles = {overview: 'A closer look at your rivers.', stations: 'Every station, one place.', events: 'Follow up on what matters.', history: 'A record of every reading.', river_explorer: 'Explore the river network.'};
const descriptions = {
    overview: 'Review conditions, investigate events and keep your records up to date.',
    stations: 'Search, filter and select a station to review its available evidence.',
    events: 'Inspect pollution events, repeated exceedances and priority locations.',
    history: 'Review past samples, correct a reading or export the records you need.',
    river_explorer: 'Trace connected river reaches and monitoring stations using Java BFS or DFS.'
};

function escape_text(value) {
    return String(value ?? '').replace(/[&<>"']/g, letter => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[letter]));
}

function badge(reading) {
    const status = reading ? reading.severity : 'NO DATA';
    return '<span class="badge ' + (reading ? reading.severity : '') + '">' + status + '</span>';
}

function type_badge(reading) {
    if (!reading) return '';
    return reading.is_annual ? '<span class="badge" style="background:#e0f2fe;color:#0369a1;border-color:#bae6fd">ANNUAL</span>' : '<span class="badge" style="background:#f3e8ff;color:#6b21a8;border-color:#e9d5ff">SAMPLE</span>';
}

function date_text(time) {
    if (!time) return 'No sample';
    const s = String(time);
    if (s.length === 4 && !isNaN(Number(s))) return 'Year ' + s;
    return s.replace('T', ' ').slice(0, 16);
}

function format_value(reading) {
    if (!reading) return '—';
    if (reading.is_annual && reading.min !== undefined && reading.max !== undefined) {
        if (reading.min === reading.max) return reading.min + ' ' + (reading.unit || '');
        return reading.min + ' – ' + reading.max + ' ' + (reading.unit || '');
    }
    return reading.value + ' ' + (reading.unit || '');
}

function empty(text) {
    return '<div class="empty">' + escape_text(text) + '</div>';
}

function local_now() {
    const now = new Date();
    return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 19);
}

function message(text, error = false) {
    find('message').textContent = text;
    find('message').className = error ? 'error' : '';
    find('message').hidden = !text;
}

function show_view(view) {
    if (!names[view]) return;
    if (active_view === 'river_explorer' && view !== 'river_explorer') pause_trace_playback();
    active_view = view;
    document.querySelectorAll('.view').forEach(section => section.hidden = section.id !== view);
    document.querySelectorAll('nav [data-view]').forEach(button => {
        button.classList.toggle('active', button.dataset.view === view);
        if (button.dataset.view === view) button.setAttribute('aria-current', 'page');
        else button.removeAttribute('aria-current');
    });
    find('content_layout').classList.toggle('explorer_mode', view === 'river_explorer');
    find('focus_panel').hidden = view === 'river_explorer';
    find('breadcrumb').textContent = names[view];
    find('page_title').textContent = titles[view];
    find('page_description').textContent = descriptions[view];
    if (view === 'river_explorer') {
        find('explorer_start').value = settings.station;
        draw_river_map();
        render_explorer_results();
    }
}

async function load(changes = {}) {
    const candidate = {...settings, ...changes};
    const context_changed = state && (candidate.station !== settings.station || candidate.pollutant !== settings.pollutant || candidate.until !== settings.until);
    if (context_changed) clear_explorer_results('The selection changed. Run a new trace or route.');
    const number = ++request_number;
    document.querySelector('main').setAttribute('aria-busy', 'true');
    try {
        const query = new URLSearchParams(candidate);
        const [state_response, network_response] = await Promise.all([
            fetch('/api/state?' + query),
            fetch('/api/network?' + new URLSearchParams({pollutant: candidate.pollutant, until: candidate.until}) )
        ]);
        const [data, network_data] = await Promise.all([state_response.json(), network_response.json()]);
        if (number !== request_number) return false;
        if (!state_response.ok) throw new Error(data.error);
        if (!network_response.ok) throw new Error(network_data.error || 'Could not load the river network.');
        if (!river_geometry) {
            const geometry_response = await fetch('/network.geojson');
            if (!geometry_response.ok) throw new Error('The original river geometry could not be loaded.');
            river_geometry = await geometry_response.json();
        }
        if (number !== request_number) return false;
        settings = candidate;
        state = data;
        river_network = network_data;
        if (!find('station').options.length) {
            const options = state.stations.map(station => '<option value="' + escape_text(station.id) + '">' + escape_text(station.name) + ' (' + escape_text(station.id) + ')</option>').join('');
            find('station').innerHTML = options;
            find('new_station').innerHTML = options;
            find('explorer_start').innerHTML = options;
            find('explorer_destination').innerHTML = options;
            find('explorer_destination').value = state.stations.some(station => station.id === '1071') ? '1071' : state.stations[0].id;
            const regions = [...new Set(state.stations.map(station => station.region))].sort();
            find('region').innerHTML = '<option value="">All regions</option>' + regions.map(region => '<option>' + escape_text(region) + '</option>').join('');
        }
        find('station').value = settings.station;
        find('pollutant').value = settings.pollutant;
        find('until').value = settings.until;
        find('explorer_start').value = settings.station;
        find('new_station').value = settings.station;
        if (!find('explorer_destination').value) find('explorer_destination').value = state.stations.some(station => station.id === '1071') ? '1071' : state.stations[0].id;
        render();
        return true;
    } catch (error) {
        if (number === request_number) {
            find('station').value = settings.station;
            find('pollutant').value = settings.pollutant;
            find('until').value = settings.until;
            message(error.message || 'Cannot reach the Java server. Keep the run window open.', true);
        }
        return false;
    } finally {
        if (number === request_number) document.querySelector('main').setAttribute('aria-busy', 'false');
    }
}

function render() {
    find('station_count').textContent = state.stations.length;
    find('abnormal').textContent = state.abnormal;
    find('event_count').textContent = state.events.length;
    find('limit').textContent = state.limit + ' ' + (state.unit || '');
    find('threshold_rule').textContent = state.rule || 'CPCB Standard';
    find('view_badge').textContent = settings.until ? 'Cutoff: ' + date_text(settings.until) : 'Latest available records';
    render_focus();
    render_overview();
    render_stations();
    render_events();
    render_history();
    draw_river_map();
    render_explorer_results();
}

function focus_station() {
    const found = state.stations.find(station => station.id === settings.station);
    return found || state.stations[0];
}

function render_focus() {
    const station = focus_station();
    find('focus_id').textContent = station.id;
    find('focus_name').textContent = station.name;
    find('focus_region').textContent = station.region + ' · Monitoring station';
    find('focus_status').innerHTML = badge(station.reading);
    find('focus_sample').textContent = settings.pollutant + ' (' + (state.rule || '') + '): ' + (station.reading ? date_text(station.reading.time) + ' · ' + format_value(station.reading) : 'No record');
    find('metrics').innerHTML = state.metrics.map(metric => '<div class="metric"><div><span>' + metric.pollutant + '</span><small>' + escape_text(metric.rule) + '</small></div><div><strong>' + format_value(metric.reading) + '</strong>' + badge(metric.reading) + '</div></div>').join('');
    let explanation = 'No record is available for ' + settings.pollutant + ' at the selected period/cutoff. Try another period or add a sample.';
    if (station.reading) {
        const is_flagged = station.reading.severity !== 'NORMAL';
        const type_str = station.reading.is_annual ? 'annual summary' : 'sample reading';
        explanation = is_flagged
            ? 'The latest available ' + settings.pollutant + ' ' + type_str + ' (' + format_value(station.reading) + ') exceeds the standard (' + state.rule + '). Trace upstream for candidate contributing reaches or downstream for potentially exposed areas.'
            : 'The latest available ' + settings.pollutant + ' ' + type_str + ' (' + format_value(station.reading) + ') is within standard limits (' + state.rule + '). Earlier pollution events may still be present in its history.';
    }
    find('focus_explanation').textContent = explanation;
}

function render_overview() {
    const stations = state.stations.filter(station => station.reading && station.reading.severity !== 'NORMAL');
    stations.sort((first, second) => second.reading.value - first.reading.value);
    find('attention').innerHTML = stations.slice(0, 4).map(station => '<button class="station_card ' + (station.id === settings.station ? 'selected' : '') + '" data-focus="' + escape_text(station.id) + '"><b>' + escape_text(station.name) + '</b><small>' + escape_text(station.region) + ' · ' + date_text(station.reading.time) + '</small><span class="card_bottom"><span class="card_value">' + format_value(station.reading) + '</span>' + badge(station.reading) + '</span></button>').join('') || empty('No station is above the selected threshold in this period.');
    find('overview_events').innerHTML = state.events.slice(0, 4).map(event_row).join('') || empty('No pollution events for this selection.');
}

function event_row(event) {
    const index = state.events.indexOf(event);
    return '<div class="event_row"><div class="event_info"><b>' + escape_text(event.station) + '</b><small>' + settings.pollutant + ' · ' + date_text(event.reading.time) + ' · ' + type_badge(event.reading) + '</small></div><div class="event_value">' + format_value(event.reading) + '<br>' + badge(event.reading) + '</div><button class="button ghost" data-event="' + index + '">Inspect</button></div>';
}

function render_stations() {
    const search = find('station_search').value.toLowerCase().trim();
    const region = find('region').value;
    const status = find('station_status').value;
    const rows = state.stations.filter(station => {
        if (search && !(station.name + ' ' + station.id + ' ' + station.region).toLowerCase().includes(search)) return false;
        if (region && station.region !== region) return false;
        if (status === 'none') return !station.reading;
        if (status === 'abnormal') return station.reading && station.reading.severity !== 'NORMAL';
        return !status || station.reading?.severity === status;
    });
    rows.sort((first, second) => {
        if (find('station_sort').value === 'risk') return (second.reading?.value ?? -1) - (first.reading?.value ?? -1);
        if (find('station_sort').value === 'date') return String(second.reading?.time || '').localeCompare(String(first.reading?.time || ''));
        return first.name.localeCompare(second.name);
    });
    find('station_matches').textContent = rows.length + ' of ' + state.stations.length;
    find('station_rows').innerHTML = rows.map(station => '<tr class="' + (station.id === settings.station ? 'selected_row' : '') + '"><td><b>' + escape_text(station.name) + '</b><small>' + escape_text(station.id + ' · ' + station.region) + '</small></td><td>' + format_value(station.reading) + ' ' + type_badge(station.reading) + '</td><td>' + badge(station.reading) + '</td><td>' + date_text(station.reading?.time) + '</td><td><button class="button ghost table_action" data-focus="' + escape_text(station.id) + '">Select</button></td></tr>').join('') || '<tr><td colspan="5">No matching stations. Try clearing the filters.</td></tr>';
}

function render_events() {
    const rows = state.events.filter(event => (find('event_scope').value === 'all' || event.id === settings.station) && (!find('event_severity').value || event.reading.severity === find('event_severity').value));
    if (find('event_sort').value === 'newest') rows.sort((a, b) => String(b.reading.time).localeCompare(String(a.reading.time)));
    if (find('event_sort').value === 'oldest') rows.sort((a, b) => String(a.reading.time).localeCompare(String(b.reading.time)));
    find('event_matches').textContent = rows.length + ' events';
    find('event_rows').innerHTML = rows.map(event_row).join('') || empty('No events match these filters.');
    find('recurring').innerHTML = state.recurring.map(row => '<div class="event_row"><div class="event_info"><b>' + escape_text(row.name) + '</b><small>' + settings.pollutant + ' exceeded across multiple reporting periods</small></div><span class="count_label">' + row.days + ' periods</span><button class="button ghost" data-focus="' + escape_text(row.id) + '" data-history="yes">Review</button></div>').join('') || empty('No repeated exceedances across reporting periods in this cutoff.');
    find('critical').innerHTML = state.critical.map(row => '<div class="event_row"><div class="event_info"><b>' + escape_text(row.name) + '</b><small>' + escape_text(row.type) + '</small></div><span class="count_label">Score ' + row.score.toFixed(2) + '</span>' + (state.stations.some(station => station.id === row.id) ? '<button class="button ghost" data-focus="' + escape_text(row.id) + '">Select</button>' : '') + '</div>').join('') || empty('No location has a priority score above 1.0.');
}

function history_rows() {
    const search = find('history_search').value.toLowerCase().trim();
    const status = find('history_status').value;
    return state.history.filter(reading => {
        if (search && !String(reading.time).toLowerCase().includes(search) && !(reading.source || '').toLowerCase().includes(search)) return false;
        if (status && reading.severity !== status) return false;
        return true;
    }).slice().reverse();
}

function render_history() {
    const rows = history_rows();
    find('history_title').textContent = focus_station().name + ' · ' + settings.pollutant;
    find('replay_label').textContent = settings.until ? 'Cutoff: ' + date_text(settings.until) : 'Latest available records';
    find('history_rows').innerHTML = rows.map(reading => '<tr><td>' + date_text(reading.time) + '</td><td>' + format_value(reading) + '</td><td>' + type_badge(reading) + '</td><td>' + badge(reading) + '</td><td><button class="button ghost table_action" data-edit="' + state.history.indexOf(reading) + '">Edit</button><button class="text_button table_action delete_action" data-delete="' + state.history.indexOf(reading) + '">Delete</button></td></tr>').join('') || '<tr><td colspan="5">No records match this time and these filters.</td></tr>';
    find('history_count').textContent = rows.length + ' of ' + state.history.length + ' available records shown.';
    find('previous').disabled = !state.times.length || (settings.until && !state.times.some(time => time < settings.until));
    find('next').disabled = !state.times.length || (settings.until && !state.times.some(time => time > settings.until));
}


function pause_trace_playback() {
    if (trace_timer !== null) {
        clearInterval(trace_timer);
        trace_timer = null;
    }
    if (find('play_trace')) find('play_trace').textContent = 'Play';
}

function clear_explorer_results(text) {
    trace_request++;
    pause_trace_playback();
    trace_data = null;
    route_data = null;
    trace_revealed = 0;
    if (find('explorer_results')) find('explorer_results').innerHTML = '<div class="empty">' + escape_text(text || 'Choose a starting station, direction and algorithm, then select Trace river.') + '</div>';
    if (find('trace_progress_label')) find('trace_progress_label').textContent = 'Run a trace to inspect the returned traversal.';
    if (find('play_trace')) find('play_trace').disabled = true;
    if (find('trace_step')) find('trace_step').disabled = true;
    if (find('trace_all')) find('trace_all').disabled = true;
    if (find('reset_trace')) find('reset_trace').disabled = true;
    apply_map_highlights();
}

function map_coordinates(point) {
    const span_x = map_bounds.max_x - map_bounds.min_x || 1;
    const span_y = map_bounds.max_y - map_bounds.min_y || 1;
    const scale = Math.min(760 / (span_x * 0.88), 450 / span_y);
    const width = span_x * 0.88 * scale;
    const height = span_y * scale;
    return [(900 - width) / 2 + (point[0] - map_bounds.min_x) * 0.88 * scale,
        (550 - height) / 2 + (map_bounds.max_y - point[1]) * scale];
}

function draw_river_map() {
    if (!river_geometry || !river_network || !find('river_map')) return;
    const svg = find('river_map');
    if (!map_bounds) {
        const points = river_geometry.features.flatMap(feature => feature.geometry.coordinates);
        map_bounds = {
            min_x: Math.min(...points.map(point => point[0])),
            max_x: Math.max(...points.map(point => point[0])),
            min_y: Math.min(...points.map(point => point[1])),
            max_y: Math.max(...points.map(point => point[1]))
        };
    }
    const segments = new Map(river_network.segments.map(segment => [segment.id, segment]));
    let html = '<text class="map_title" x="24" y="28">GANGA BASIN / HYDRORIVERS REACHES</text><text class="map_title" x="855" y="28">N ↑</text><g id="map_layer">';
    for (const feature of river_geometry.features) {
        const points = feature.geometry.coordinates.map(point => map_coordinates(point).join(',')).join(' ');
        html += '<polyline class="river_line" data-reach="' + escape_text(feature.properties.id) + '" points="' + points + '" />';
    }
    for (const station of river_network.stations) {
        const segment = segments.get(station.segment_id);
        if (!segment) continue;
        const point = map_coordinates([segment.longitude, segment.latitude]);
        const active = station.id === settings.station;
        const status_class = station.status === 'NO DATA' ? 'missing_station' : station.status === 'NORMAL' ? 'normal_station' : 'flagged_station';
        html += '<g class="station_pin ' + status_class + (active ? ' selected_station' : '') + '" data-station="' + escape_text(station.id) + '" role="button" tabindex="0" aria-label="Select ' + escape_text(station.name) + '">';
        if (active) html += '<circle class="station_halo" cx="' + point[0] + '" cy="' + point[1] + '" r="13"></circle>';
        html += '<circle class="station_dot" cx="' + point[0] + '" cy="' + point[1] + '" r="' + (active ? 6.5 : 5) + '"><title>' + escape_text(station.name) + ' · ' + escape_text(station.status) + '</title></circle>';
        if (active) html += '<text class="map_label" x="' + (point[0] - 9) + '" y="' + (point[1] - 17) + '" text-anchor="end">' + escape_text(station.name) + '</text>';
        html += '</g>';
    }
    html += '</g>';
    svg.innerHTML = html;
    svg.querySelectorAll('[data-station]').forEach(marker => {
        marker.addEventListener('click', () => select_explorer_station(marker.dataset.station));
        marker.addEventListener('keydown', event => {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                select_explorer_station(marker.dataset.station);
            }
        });
    });
    set_map_transform();
    apply_map_highlights();
}

function apply_map_highlights() {
    const svg = find('river_map');
    if (!svg) return;
    const visited = new Set();
    if (trace_data) {
        trace_data.segments.slice(0, trace_revealed).forEach(segment => visited.add(segment.id));
    }
    const route = new Set(route_data && route_data.found ? route_data.segments : []);
    svg.querySelectorAll('[data-reach]').forEach(line => {
        const id = line.dataset.reach;
        line.classList.toggle('trace_visited', visited.has(id));
        line.classList.toggle('route_selected', route.has(id));
    });
}

function station_status_markup(station) {
    const reading = station.reading;
    const status = station.status || 'NO DATA';
    const value = reading ? format_value(reading) : 'No reading available';
    const time = reading ? date_text(reading.time) : 'Missing data';
    return '<span class="badge ' + (status === 'NO DATA' ? 'NO_DATA' : status) + '">' + escape_text(status) + '</span><small>' + escape_text(station.region) + ' · ' + escape_text(value) + ' · ' + escape_text(time) + '</small>';
}

function render_explorer_results() {
    if (!find('explorer_results')) return;
    if (trace_data) {
        const shown_segments = new Set(trace_data.segments.slice(0, trace_revealed).map(segment => segment.id));
        const reached = trace_data.stations.filter(station => shown_segments.has(station.segment_id));
        const flagged = reached.filter(station => station.reading && station.reading.severity !== 'NORMAL');
        const missing = reached.filter(station => !station.reading);
        const direction_label = trace_data.direction === 'upstream' ? 'Candidate upstream stations' : 'Connected downstream stations';
        const rows = reached.length ? '<div class="result_station_list">' + reached.map(station => '<div class="result_station"><div><b>' + escape_text(station.name) + ' <span>(' + escape_text(station.id) + ')</span></b>' + station_status_markup(station) + '</div></div>').join('') + '</div>' : '<div class="empty">' + (trace_revealed === 0 ? 'No traversal steps are displayed yet.' : 'No additional monitoring stations are mapped to the displayed segments.') + '</div>';
        find('explorer_results').innerHTML = '<div class="result_heading"><b>' + escape_text(trace_data.algorithm) + ' · ' + escape_text(trace_data.direction) + '</b><span>' + trace_revealed + ' / ' + trace_data.segment_count + ' segments shown</span></div>' +
            '<div class="result_stats"><div><small>River segments visited</small><strong>' + trace_revealed + ' / ' + trace_data.segment_count + '</strong></div><div><small>Monitoring stations reached</small><strong>' + reached.length + ' / ' + trace_data.station_count + '</strong></div><div><small>Flagged stations</small><strong>' + flagged.length + ' / ' + trace_data.flagged_count + '</strong></div></div>' +
            '<p class="hint result_hint">' + direction_label + '. Stations mapped to the starting reach are excluded because their within-reach order is unknown. Flagged stations are ' + (trace_data.direction === 'upstream' ? 'candidates for investigation, not proven sources.' : 'connected locations, not confirmed polluted sites.') + '</p>' +
            (missing.length ? '<p class="missing_note">' + missing.length + ' reached station(s) have no reading for the selected parameter and time. They are not counted as normal.</p>' : '') + rows;
        find('trace_progress_label').textContent = 'Traversal progress: ' + trace_revealed + ' of ' + trace_data.segment_count + ' river segments shown.';
        find('play_trace').disabled = trace_data.segment_count === 0;
        find('trace_step').disabled = trace_revealed >= trace_data.segment_count;
        find('trace_all').disabled = trace_revealed >= trace_data.segment_count;
        find('reset_trace').disabled = trace_revealed === 0;
        apply_map_highlights();
        return;
    }
    if (route_data) {
        if (!route_data.found) {
            find('explorer_results').innerHTML = '<div class="route_message no_route"><b>No downstream route found.</b><p>No directed downstream path connects ' + escape_text(route_data.start_name) + ' to ' + escape_text(route_data.destination_name) + ' in the included river network. Any previous route highlight has been cleared.</p></div>';
        } else {
            let note = route_data.same_station ? 'The start and destination are the same station, so the valid route contains one mapped segment and zero graph edges.' : route_data.same_segment ? 'Both stations map to the same river segment. The source data does not provide within-segment station order.' : 'The route follows directed downstream links. BFS finds a path with the fewest graph edges; the result is not kilometre-minimised.';
            find('explorer_results').innerHTML = '<div class="route_message"><b>Downstream route found</b><div class="route_stats"><span><strong>' + route_data.segments.length + '</strong> segments</span><span><strong>' + route_data.hops + '</strong> graph edges</span></div><p>' + note + '</p><details><summary>Route segment IDs</summary><p class="segment_ids">' + route_data.segments.map(escape_text).join(' → ') + '</p></details></div>';
        }
        find('trace_progress_label').textContent = route_data.found ? 'Route displayed from ' + route_data.start_name + ' to ' + route_data.destination_name + '.' : 'No route is highlighted.';
        apply_map_highlights();
        return;
    }
    find('explorer_results').innerHTML = '<div class="empty">Choose a starting station, direction and algorithm, then select Trace river.</div>';
    find('trace_progress_label').textContent = 'Run a trace to inspect the returned traversal.';
    apply_map_highlights();
}

function select_explorer_station(id) {
    if (!state || !state.stations.some(station => station.id === id)) return;
    find('explorer_start').value = id;
    message('');
    load({station: id});
}

async function run_explorer_trace() {
    if (!state) return;
    clear_explorer_results('');
    const ticket = ++trace_request;
    const button = find('trace_river');
    button.disabled = true;
    const start = find('explorer_start').value;
    const direction = find('explorer_direction').value;
    const algorithm = find('explorer_algorithm').value;
    const query = new URLSearchParams({station: start, direction, algorithm, pollutant: settings.pollutant, until: settings.until});
    try {
        const response = await fetch('/api/trace?' + query);
        const data = await response.json();
        if (ticket !== trace_request || start !== settings.station || direction !== find('explorer_direction').value || algorithm !== find('explorer_algorithm').value) return;
        if (!response.ok) throw new Error(data.error || 'The Java trace could not be completed.');
        trace_data = data;
        trace_revealed = 0;
        route_data = null;
        render_explorer_results();
    } catch (error) {
        if (ticket === trace_request) {
            find('explorer_results').innerHTML = '<div class="empty error_text">' + escape_text(error.message) + '</div>';
        }
    } finally {
        if (ticket === trace_request) button.disabled = false;
    }
}

function reveal_trace_step() {
    if (!trace_data || trace_revealed >= trace_data.segment_count) return;
    trace_revealed++;
    render_explorer_results();
}

function play_trace_playback() {
    if (trace_timer !== null) {
        pause_trace_playback();
        return;
    }
    if (!trace_data || trace_data.segment_count === 0) return;
    if (trace_revealed >= trace_data.segment_count) trace_revealed = 0;
    find('play_trace').textContent = 'Pause';
    render_explorer_results();
    trace_timer = setInterval(() => {
        if (!trace_data || active_view !== 'river_explorer') {
            pause_trace_playback();
            return;
        }
        trace_revealed++;
        render_explorer_results();
        if (trace_revealed >= trace_data.segment_count) pause_trace_playback();
    }, 180);
}

function reset_trace_progress() {
    pause_trace_playback();
    if (!trace_data) return;
    trace_revealed = 0;
    route_data = null;
    render_explorer_results();
}

async function find_downstream_route() {
    if (!state) return;
    clear_explorer_results('');
    const ticket = ++trace_request;
    const button = find('find_route');
    button.disabled = true;
    const start = find('explorer_start').value;
    const destination = find('explorer_destination').value;
    const query = new URLSearchParams({start, destination, pollutant: settings.pollutant, until: settings.until});
    try {
        const response = await fetch('/api/route?' + query);
        const data = await response.json();
        if (ticket !== trace_request || start !== settings.station || destination !== find('explorer_destination').value) return;
        if (!response.ok) throw new Error(data.error || 'The Java route search could not be completed.');
        route_data = data;
        trace_data = null;
        render_explorer_results();
    } catch (error) {
        if (ticket === trace_request) find('explorer_results').innerHTML = '<div class="empty error_text">' + escape_text(error.message) + '</div>';
    } finally {
        if (ticket === trace_request) button.disabled = false;
    }
}

function set_map_transform() {
    const layer = find('map_layer');
    if (layer) layer.setAttribute('transform', 'translate(' + map_view.x + ' ' + map_view.y + ') scale(' + map_view.scale + ')');
}

function zoom_map(factor, anchor = {x: 450, y: 275}) {
    const next = Math.max(1, Math.min(8, map_view.scale * factor));
    const ratio = next / map_view.scale;
    map_view.x = anchor.x - (anchor.x - map_view.x) * ratio;
    map_view.y = anchor.y - (anchor.y - map_view.y) * ratio;
    map_view.scale = next;
    set_map_transform();
}

function reset_map_view() {
    map_view = {scale: 1, x: 0, y: 0};
    set_map_transform();
}

function svg_point(event) {
    const svg = find('river_map');
    const point = svg.createSVGPoint();
    point.x = event.clientX;
    point.y = event.clientY;
    return point.matrixTransform(svg.getScreenCTM().inverse());
}

function setup_map_controls() {
    const svg = find('river_map');
    svg.addEventListener('wheel', event => {
        event.preventDefault();
        const point = svg_point(event);
        zoom_map(event.deltaY < 0 ? 1.18 : 1 / 1.18, point);
    }, {passive: false});
    svg.addEventListener('pointerdown', event => {
        if (event.button !== 0 || event.target.closest('[data-station]')) return;
        map_drag = {point: svg_point(event), x: map_view.x, y: map_view.y};
        svg.setPointerCapture(event.pointerId);
        svg.classList.add('dragging');
    });
    svg.addEventListener('pointermove', event => {
        if (!map_drag) return;
        const point = svg_point(event);
        map_view.x = map_drag.x + point.x - map_drag.point.x;
        map_view.y = map_drag.y + point.y - map_drag.point.y;
        set_map_transform();
    });
    const stop_drag = () => { map_drag = null; svg.classList.remove('dragging'); };
    svg.addEventListener('pointerup', stop_drag);
    svg.addEventListener('pointercancel', stop_drag);
    find('zoom_in').addEventListener('click', () => zoom_map(1.25));
    find('zoom_out').addEventListener('click', () => zoom_map(0.8));
    find('reset_view').addEventListener('click', reset_map_view);
}

async function step(direction) {
    if (!state || !state.times.length) return;
    let index = -1;
    if (!settings.until) index = direction > 0 ? 0 : state.times.length - 1;
    else if (direction > 0) index = state.times.findIndex(time => time > settings.until);
    else for (let i = 0; i < state.times.length; i++) if (state.times[i] < settings.until) index = i;
    if (index < 0) return;
    await load({until: state.times[index]});
}

function open_reading(index = null) {
    if (!state) return;
    editing = index === null ? null : {...state.history[index], station: settings.station};
    find('reading_form').reset();
    find('new_station').value = editing ? editing.station : settings.station;
    find('new_pollutant').value = editing ? editing.pollutant : settings.pollutant;
    find('new_station').disabled = !!editing;
    find('new_pollutant').disabled = !!editing;
    find('new_value').value = editing ? editing.value : '';
    find('new_time').value = editing ? editing.time : local_now();
    find('reading_title').textContent = editing ? 'Edit a reading' : 'Add a reading';
    find('form_message').textContent = '';
    update_unit_hint();
    find('reading_dialog').showModal();
}

function update_unit_hint() {
    const pol = find('new_pollutant').value;
    const units = {BOD: 'mg/L', DO: 'mg/L', PH: 'pH', FC: 'MPN/100 mL'};
    find('new_value_label').childNodes[0].textContent = 'Value (' + (units[pol] || '') + ') ';
}

async function post(path, values) {
    const response = await fetch(path, {method: 'POST', body: new URLSearchParams(values)});
    const data = await response.json();
    if (!response.ok) throw new Error(data.error);
    return data;
}

async function save_reading(event) {
    event.preventDefault();
    if (saving) return;
    saving = true;
    find('save_reading').disabled = true;
    find('form_message').textContent = '';
    const values = {station: find('new_station').value, pollutant: find('new_pollutant').value, value: find('new_value').value, time: find('new_time').value};
    if (editing) {
        values.old_time = editing.time;
        values.old_value = editing.value;
    }
    try {
        const data = await post(editing ? '/api/update' : '/api/readings', values);
        find('reading_dialog').close();
        message(data.message);
        clear_explorer_results('A reading was changed. Run a new trace to refresh station statuses.');
        await load({station: values.station, pollutant: values.pollutant, until: ''});
        show_view('history');
    } catch (error) {
        find('form_message').textContent = error.message;
    } finally {
        saving = false;
        find('save_reading').disabled = false;
    }
}

function open_delete(index) {
    if (!state) return;
    deleting = {...state.history[index], station: settings.station};
    find('delete_summary').textContent = focus_station().name + ' · ' + deleting.pollutant + ' · ' + format_value(deleting) + ' · ' + date_text(deleting.time);
    find('delete_message').textContent = '';
    find('delete_dialog').showModal();
}

async function delete_reading() {
    if (!deleting || saving) return;
    saving = true;
    find('confirm_delete').disabled = true;
    try {
        const data = await post('/api/delete', {station: deleting.station, pollutant: deleting.pollutant, old_time: deleting.time, old_value: deleting.value});
        find('delete_dialog').close();
        message(data.message);
        clear_explorer_results('A reading was deleted. Run a new trace to refresh station statuses.');
        await load();
    } catch (error) {
        find('delete_message').textContent = error.message;
    } finally {
        saving = false;
        find('confirm_delete').disabled = false;
    }
}

function clear_history() {
    find('history_search').value = '';
    find('history_status').value = '';
    if (state) render_history();
}

function export_history() {
    if (!state) return;
    const rows = ['station,pollutant,value,min_value,max_value,time,type,severity,source'];
    for (const reading of history_rows()) {
        rows.push([
            settings.station,
            reading.pollutant,
            reading.value,
            reading.min ?? reading.value,
            reading.max ?? reading.value,
            reading.time,
            reading.is_annual ? 'ANNUAL' : 'SAMPLE',
            reading.severity,
            reading.source || ''
        ].join(','));
    }
    const url = URL.createObjectURL(new Blob([rows.join('\n')], {type: 'text/csv'}));
    const link = document.createElement('a');
    link.href = url;
    link.download = settings.station + '_' + settings.pollutant + '_history.csv';
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}

document.addEventListener('click', async event => {
    const button = event.target.closest('button');
    if (!button) return;
    if (button.dataset.view) show_view(button.dataset.view);
    if (button.hasAttribute('data-add')) open_reading();
    if (button.dataset.close && !saving) find(button.dataset.close).close();
    if (button.dataset.explorerStation && state) select_explorer_station(button.dataset.explorerStation);
    if (button.dataset.focus && state) {
        message('');
        if (await load({station: button.dataset.focus}) && button.dataset.history) {
            clear_history();
            show_view('history');
        }
    }
    if (button.dataset.event !== undefined && state) {
        const selected = state.events[Number(button.dataset.event)];
        if (await load({station: selected.id, until: String(selected.reading.time)})) {
            clear_history();
            show_view('history');
            message('Inspecting ' + selected.station + ' at ' + date_text(selected.reading.time) + '. Use Return to latest when finished.');
        }
    }
    if (button.dataset.edit !== undefined && state) open_reading(Number(button.dataset.edit));
    if (button.dataset.delete !== undefined && state) open_delete(Number(button.dataset.delete));
});

find('station').addEventListener('change', () => load({station: find('station').value}));
find('pollutant').addEventListener('change', () => load({pollutant: find('pollutant').value}));
find('new_pollutant').addEventListener('change', update_unit_hint);
find('apply_time').addEventListener('click', () => { message(''); load({until: find('until').value}); });
find('latest').addEventListener('click', () => { message(''); load({until: ''}); });
find('refresh').addEventListener('click', () => { message(''); load(); });
for (const id of ['station_search', 'region', 'station_status', 'station_sort']) find(id).addEventListener('input', () => { if (state) render_stations(); });
for (const id of ['event_scope', 'event_severity', 'event_sort']) find(id).addEventListener('change', () => { if (state) render_events(); });
find('history_search').addEventListener('input', () => { if (state) render_history(); });
find('history_status').addEventListener('change', () => { if (state) render_history(); });
find('clear_history').addEventListener('click', clear_history);
find('open_trace').addEventListener('click', () => show_view('river_explorer'));
find('try_trace').addEventListener('click', async () => { if (await load({station: '1067'})) show_view('river_explorer'); });
find('explorer_start').addEventListener('change', () => select_explorer_station(find('explorer_start').value));
find('explorer_direction').addEventListener('change', () => clear_explorer_results('Direction changed. Run Trace river to calculate a new traversal.'));
find('explorer_algorithm').addEventListener('change', () => clear_explorer_results('Algorithm changed. Run Trace river to calculate a new traversal.'));
find('explorer_destination').addEventListener('change', () => clear_explorer_results('Destination changed. Run a new trace or route.'));
find('trace_river').addEventListener('click', run_explorer_trace);
find('play_trace').addEventListener('click', play_trace_playback);
find('trace_step').addEventListener('click', reveal_trace_step);
find('trace_all').addEventListener('click', () => { if (trace_data) { trace_revealed = trace_data.segment_count; pause_trace_playback(); render_explorer_results(); } });
find('reset_trace').addEventListener('click', reset_trace_progress);
find('find_route').addEventListener('click', find_downstream_route);
find('previous').addEventListener('click', () => step(-1));
find('next').addEventListener('click', () => step(1));
find('reading_form').addEventListener('submit', save_reading);
find('confirm_delete').addEventListener('click', delete_reading);
find('export').addEventListener('click', export_history);
for (const id of ['reading_dialog', 'delete_dialog']) find(id).addEventListener('cancel', event => { if (saving) event.preventDefault(); });
setup_map_controls();
load();
