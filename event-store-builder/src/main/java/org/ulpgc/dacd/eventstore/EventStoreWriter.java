package org.ulpgc.dacd.eventstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class EventStoreWriter {
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE.withZone(ZoneOffset.UTC);

    private final Path baseDirectory;
    private final ObjectMapper objectMapper;

    public EventStoreWriter(String baseDirectory) {
        this(Path.of(baseDirectory), new ObjectMapper());
    }

    public EventStoreWriter(Path baseDirectory, ObjectMapper objectMapper) {
        this.baseDirectory = baseDirectory;
        this.objectMapper = objectMapper;
    }

    public void append(String topic, String eventJson) {
        if (isBlank(eventJson)) {
            System.out.println("Evento ignorado en eventstore: JSON vacio.");
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(eventJson);
            String ts = readRequiredText(root, "ts");
            String ss = readRequiredText(root, "ss");

            if (isBlank(ts) || isBlank(ss)) {
                System.out.println("Evento ignorado en eventstore: faltan campos ts o ss.");
                return;
            }

            String day = dayFromTimestamp(ts);
            Path directory = baseDirectory.resolve(topic).resolve(ss);
            Files.createDirectories(directory);
            Files.writeString(
                    directory.resolve(day + ".events"),
                    eventJson.strip() + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (JsonProcessingException e) {
            System.out.println("No se pudo parsear evento JSON para eventstore: " + e.getOriginalMessage());
        } catch (DateTimeParseException e) {
            System.out.println("No se pudo interpretar ts como timestamp UTC para eventstore: " + e.getParsedString());
        } catch (IOException e) {
            System.out.println("No se pudo escribir evento en eventstore: " + e.getMessage());
        }
    }

    private String readRequiredText(JsonNode root, String fieldName) {
        JsonNode field = root.get(fieldName);
        return field != null && field.isTextual() ? field.asText() : null;
    }

    private String dayFromTimestamp(String timestamp) {
        return DAY_FORMATTER.format(Instant.parse(timestamp));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
