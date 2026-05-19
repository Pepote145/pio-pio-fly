package org.ulpgc.dacd.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class EventMessage {
    private String ts;
    private String ss;
    private Map<String, Object> payload;

    public EventMessage() {
        this.payload = new LinkedHashMap<>();
    }

    public EventMessage(String ts, String ss, Map<String, Object> payload) {
        this.ts = ts;
        this.ss = ss;
        this.payload = payload != null ? new LinkedHashMap<>(payload) : new LinkedHashMap<>();
    }

    public static EventMessage capturedNow(String source, Map<String, Object> payload) {
        return new EventMessage(Instant.now().toString(), source, payload);
    }

    public static EventMessage fromInstant(Instant timestampUtc, String source, Map<String, Object> payload) {
        Instant eventTimestamp = timestampUtc != null ? timestampUtc : Instant.now();
        return new EventMessage(eventTimestamp.toString(), source, payload);
    }

    public String getTs() {
        return ts;
    }

    public String getSs() {
        return ss;
    }

    public Map<String, Object> getPayload() {
        return payload != null ? new LinkedHashMap<>(payload) : new LinkedHashMap<>();
    }

    @Override
    public String toString() {
        return "EventMessage{" +
                "ts='" + ts + '\'' +
                ", ss='" + ss + '\'' +
                ", payload=" + payload +
                '}';
    }
}
