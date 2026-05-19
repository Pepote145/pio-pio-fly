package org.ulpgc.dacd.eventstore;

import org.ulpgc.dacd.domain.ActiveMqEventPublisher;
import org.ulpgc.dacd.domain.EventMessage;
import org.ulpgc.dacd.domain.EventTopics;

import java.util.LinkedHashMap;
import java.util.Map;

public class EventStoreManualPublisher {
    private static final String BROKER_URL = ActiveMqEventPublisher.DEFAULT_BROKER_URL;
    private static final String SOURCE_ID = "manual-test-source";

    public static void main(String[] args) {
        System.out.println("Conectando a ActiveMQ en " + BROKER_URL + "...");

        try (ActiveMqEventPublisher publisher = new ActiveMqEventPublisher(BROKER_URL)) {
            System.out.println("Conexion con ActiveMQ preparada.");
            publishAwayMatchEvent(publisher);
            publishFlightInfoEvent(publisher);
            System.out.println("Publicacion manual finalizada.");
        } catch (IllegalStateException e) {
            System.out.println("No se pudo publicar eventos manuales en ActiveMQ: " + e.getMessage());
        }
    }

    private static void publishAwayMatchEvent(ActiveMqEventPublisher publisher) {
        publisher.publish(EventTopics.AWAY_MATCH, EventMessage.capturedNow(SOURCE_ID, awayMatchPayload()));
        System.out.println("Evento manual publicado en topic " + EventTopics.AWAY_MATCH + ".");
    }

    private static void publishFlightInfoEvent(ActiveMqEventPublisher publisher) {
        publisher.publish(EventTopics.FLIGHT_INFO, EventMessage.capturedNow(SOURCE_ID, flightInfoPayload()));
        System.out.println("Evento manual publicado en topic " + EventTopics.FLIGHT_INFO + ".");
    }

    private static Map<String, Object> awayMatchPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("externalId", "manual-away-match-001");
        payload.put("competition", "LALIGA HYPERMOTION");
        payload.put("homeTeam", "RC Deportivo");
        payload.put("awayTeam", "UD Las Palmas");
        payload.put("matchDate", "2026-05-24T18:30:00");
        payload.put("city", "A Coruna");
        payload.put("stadium", "Abanca-Riazor");
        payload.put("destinationAirport", "LCG");
        payload.put("source", "manual-test");
        return payload;
    }

    private static Map<String, Object> flightInfoPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("flightNumber", "NT6001");
        payload.put("airline", "Binter");
        payload.put("originAirport", "LPA");
        payload.put("destinationAirport", "LCG");
        payload.put("scheduledDateTime", "2026-05-24T09:00:00");
        payload.put("status", "Scheduled");
        payload.put("terminal", "T1");
        payload.put("source", "manual-test");
        return payload;
    }
}
