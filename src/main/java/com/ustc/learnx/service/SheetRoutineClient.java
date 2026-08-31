package com.ustc.learnx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Reads one tab of a published Google Sheet.
 *
 * <p>The browser cannot do this itself. Google's visualization endpoint answers
 * with JSONP, which means a script tag pointed at docs.google.com, and the
 * Content-Security-Policy this application sets is {@code script-src 'self'} --
 * so the tab is fetched here instead, parsed, and served from our own origin.
 * That also means one fetch serves every student in a class rather than one per
 * device, which is what the short cache below is for.
 *
 * <p>The sheet id and gid are the only parts of the address that vary, and both
 * are checked against a strict pattern before use: the host is fixed, so a
 * configured "sheet" cannot become a request to somewhere else.
 */
@Component
@RequiredArgsConstructor
public class SheetRoutineClient {

    private static final Logger log = LoggerFactory.getLogger(SheetRoutineClient.class);

    private static final String HOST = "https://docs.google.com";
    private static final Pattern SHEET_ID = Pattern.compile("[A-Za-z0-9_-]{8,120}");
    private static final Pattern GID = Pattern.compile("\\d{1,20}");

    /** Long enough that a class opening the app together costs one fetch; short
     *  enough that a correction posted in the sheet shows up within a lecture. */
    private static final Duration FRESH_FOR = Duration.ofMinutes(5);

    /** How long a failed fetch may be answered from the last good copy. */
    private static final Duration STALE_FOR = Duration.ofHours(12);

    /**
     * Short timeouts on purpose.
     *
     * <p>A student opening the routine is waiting for this request. If Google is
     * slow or unreachable, failing quickly and falling back to the saved copy is
     * worth far more than eventually succeeding: the screen says the data is
     * stale, and the reader decides what to do with that.
     */
    private final RestClient http = RestClient.builder()
            .baseUrl(HOST)
            .defaultHeader("Accept", "application/json, text/plain, */*")
            .requestFactory(timeouts())
            .build();

    private static ClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(6));
        return factory;
    }

    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(JsonNode table, Instant at) {}

    /** A tab's parsed table, and whether it came from a live fetch. */
    public record Tab(JsonNode table, boolean stale, Instant fetchedAt) {}

    public static boolean isValidSheetId(String sheetId) {
        return sheetId != null && SHEET_ID.matcher(sheetId).matches();
    }

    public static boolean isValidGid(String gid) {
        return gid != null && GID.matcher(gid).matches();
    }

    /**
     * One tab, from the cache when it is fresh and from Google otherwise.
     *
     * <p>A failed fetch falls back to the last good copy for this tab rather
     * than failing the whole screen: a routine that is a few hours old is worth
     * more to somebody standing outside a classroom than an error is.
     */
    public Optional<Tab> fetchTab(String sheetId, String gid) {
        if (!isValidSheetId(sheetId) || !isValidGid(gid)) {
            return Optional.empty();
        }

        String key = sheetId + "/" + gid;
        Cached cached = cache.get(key);
        if (cached != null && Duration.between(cached.at(), Instant.now()).compareTo(FRESH_FOR) < 0) {
            return Optional.of(new Tab(cached.table(), false, cached.at()));
        }

        try {
            String body = http.get()
                    .uri("/spreadsheets/d/{sheet}/gviz/tq?gid={gid}&headers=0&tqx=out:json",
                            sheetId, gid)
                    .retrieve()
                    .body(String.class);

            JsonNode table = parse(body);
            if (table != null) {
                cache.put(key, new Cached(table, Instant.now()));
                return Optional.of(new Tab(table, false, Instant.now()));
            }
            log.warn("Sheet tab {} answered with no usable table", key);
        } catch (Exception ex) {
            log.warn("Could not read sheet tab {}: {}", key, ex.toString());
        }

        if (cached != null && Duration.between(cached.at(), Instant.now()).compareTo(STALE_FOR) < 0) {
            return Optional.of(new Tab(cached.table(), true, cached.at()));
        }
        return Optional.empty();
    }

    /**
     * Unwraps the visualization endpoint's response.
     *
     * <p>It answers with JavaScript, not JSON: a comment, then a call to
     * {@code google.visualization.Query.setResponse(...)} around the payload.
     * The braces are located rather than the prefix stripped by length, because
     * that prefix has changed before.
     */
    private JsonNode parse(String body) throws Exception {
        if (body == null) return null;
        int open = body.indexOf('{');
        int close = body.lastIndexOf('}');
        if (open < 0 || close <= open) return null;

        JsonNode root = mapper.readTree(body.substring(open, close + 1));
        if (!"ok".equals(root.path("status").asText())) {
            // A tab that is not shared, or a gid that does not exist.
            return null;
        }
        JsonNode table = root.path("table");
        return table.isMissingNode() || !table.has("rows") ? null : table;
    }

    /** Drops everything cached for one sheet, so a Refresh really refetches. */
    public void evict(String sheetId) {
        cache.keySet().removeIf(key -> key.startsWith(sheetId + "/"));
    }
}
