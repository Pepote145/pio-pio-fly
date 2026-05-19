package org.ulpgc.dacd.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ulpgc.dacd.domain.EventTopics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.stream.Stream;

public class EventStoreDatamartLoader {
    private final DatamartRepository datamartRepository;
    private final Path eventStoreBasePath;
    private final ObjectMapper objectMapper;

    public EventStoreDatamartLoader(DatamartRepository datamartRepository) {
        this(datamartRepository, Path.of(BusinessUnitConfig.EVENT_STORE_BASE_PATH), new ObjectMapper());
    }

    public EventStoreDatamartLoader(DatamartRepository datamartRepository, Path eventStoreBasePath,
                                    ObjectMapper objectMapper) {
        this.datamartRepository = datamartRepository;
        this.eventStoreBasePath = eventStoreBasePath;
        this.objectMapper = objectMapper;
    }

    public DatamartLoadResult load() {
        LoadCounters counters = new LoadCounters();

        if (!Files.exists(eventStoreBasePath)) {
            return counters.toResult();
        }

        try (Stream<Path> paths = Files.walk(eventStoreBasePath)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".events"))
                    .forEach(path -> loadFile(path, counters));
        } catch (IOException e) {
            System.out.println("No se pudo recorrer el eventstore: " + e.getMessage());
            counters.failedEvents++;
        }

        return counters.toResult();
    }

    private void loadFile(Path path, LoadCounters counters) {
        String topic = extractTopic(path);
        if (isBlank(topic)) {
            System.out.println("Se omite fichero sin topic reconocible: " + path);
            counters.skippedEvents++;
            return;
        }

        try (Stream<String> lines = Files.lines(path)) {
            lines.forEach(line -> loadLine(topic, line, counters));
        } catch (IOException e) {
            System.out.println("No se pudo leer fichero de eventos " + path + ": " + e.getMessage());
            counters.failedEvents++;
        }
    }

    private void loadLine(String topic, String line, LoadCounters counters) {
        if (isBlank(line)) {
            return;
        }

        counters.processedEvents++;
        try {
            JsonNode event = objectMapper.readTree(line);
            JsonNode payload = event.get("payload");
            String ts = text(event, "ts");
            String ss = text(event, "ss");

            if (isBlank(ts) || isBlank(ss) || payload == null || !payload.isObject()) {
                counters.skippedEvents++;
                return;
            }

            if (EventTopics.AWAY_MATCH.equals(topic)) {
                if (!hasAwayMatchKey(payload, ss)) {
                    counters.skippedEvents++;
                    return;
                }
                saveAwayMatch(payload, ts, ss);
                counters.loadedAwayMatches++;
            } else if (EventTopics.FLIGHT_INFO.equals(topic)) {
                if (!hasFlightInfoKey(payload, ss)) {
                    counters.skippedEvents++;
                    return;
                }
                saveFlightInfo(payload, ts, ss);
                counters.loadedFlights++;
            } else {
                counters.skippedEvents++;
            }
        } catch (IOException | SQLException e) {
            System.out.println("No se pudo cargar evento en datamart: " + e.getMessage());
            counters.failedEvents++;
        }
    }

    private void saveAwayMatch(JsonNode payload, String capturedAt, String sourceFallback) throws SQLException {
        datamartRepository.saveAwayMatchFromEvent(
                text(payload, "externalId"),
                text(payload, "competition"),
                text(payload, "homeTeam"),
                text(payload, "awayTeam"),
                text(payload, "matchDate"),
                text(payload, "city"),
                text(payload, "stadium"),
                text(payload, "destinationAirport"),
                source(payload, sourceFallback),
                capturedAt
        );
    }

    private void saveFlightInfo(JsonNode payload, String capturedAt, String sourceFallback) throws SQLException {
        datamartRepository.saveFlightInfoFromEvent(
                text(payload, "flightNumber"),
                text(payload, "airline"),
                text(payload, "originAirport"),
                text(payload, "destinationAirport"),
                text(payload, "scheduledDateTime"),
                text(payload, "status"),
                text(payload, "terminal"),
                source(payload, sourceFallback),
                capturedAt
        );
    }

    private String extractTopic(Path path) {
        Path relativePath = eventStoreBasePath.relativize(path);
        return relativePath.getNameCount() >= 3 ? relativePath.getName(0).toString() : null;
    }

    private String source(JsonNode payload, String fallback) {
        String source = text(payload, "source");
        return isBlank(source) ? fallback : source;
    }

    private boolean hasAwayMatchKey(JsonNode payload, String sourceFallback) {
        return !isBlank(text(payload, "externalId")) && !isBlank(source(payload, sourceFallback));
    }

    private boolean hasFlightInfoKey(JsonNode payload, String sourceFallback) {
        return !isBlank(text(payload, "flightNumber"))
                && !isBlank(text(payload, "originAirport"))
                && !isBlank(text(payload, "destinationAirport"))
                && !isBlank(text(payload, "scheduledDateTime"))
                && !isBlank(source(payload, sourceFallback));
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static class LoadCounters {
        private int processedEvents;
        private int loadedAwayMatches;
        private int loadedFlights;
        private int skippedEvents;
        private int failedEvents;

        private DatamartLoadResult toResult() {
            return new DatamartLoadResult(
                    processedEvents,
                    loadedAwayMatches,
                    loadedFlights,
                    skippedEvents,
                    failedEvents
            );
        }
    }
}
