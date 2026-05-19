package org.ulpgc.dacd.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.ulpgc.dacd.domain.EventTopics;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class BusinessUnitEventSubscriber {
    private final String brokerUrl;
    private final String clientId;
    private final DatamartRepository datamartRepository;
    private final ObjectMapper objectMapper;
    private final List<MessageConsumer> consumers;

    private Connection connection;
    private Session session;
    private boolean active;

    public BusinessUnitEventSubscriber(String brokerUrl, String clientId, DatamartRepository datamartRepository) {
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
        this.datamartRepository = datamartRepository;
        this.objectMapper = new ObjectMapper();
        this.consumers = new ArrayList<>();
    }

    public boolean start() {
        if (active) {
            System.out.println("La sincronizacion en vivo ya esta activa.");
            return false;
        }

        try {
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
            connection = connectionFactory.createConnection();
            connection.setClientID(clientId);
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            createDurableSubscriber(EventTopics.AWAY_MATCH);
            createDurableSubscriber(EventTopics.FLIGHT_INFO);
            connection.start();
            active = true;
            System.out.println("Sincronizacion en vivo iniciada desde ActiveMQ.");
            return true;
        } catch (JMSException e) {
            System.out.println("No se pudo iniciar la sincronizacion en vivo con ActiveMQ: " + e.getMessage());
            closeResources();
            return false;
        }
    }

    public void stop() {
        if (!active) {
            System.out.println("La sincronizacion en vivo no esta activa.");
            return;
        }

        closeResources();
        active = false;
        System.out.println("Sincronizacion en vivo detenida.");
    }

    public boolean isActive() {
        return active;
    }

    private void createDurableSubscriber(String topicName) throws JMSException {
        Topic topic = session.createTopic(topicName);
        MessageConsumer consumer = session.createDurableSubscriber(topic, subscriptionName(topicName));
        consumer.setMessageListener(message -> handleMessage(topicName, message));
        consumers.add(consumer);
    }

    private String subscriptionName(String topicName) {
        return "business-unit-" + topicName;
    }

    private void handleMessage(String topicName, Message message) {
        if (!(message instanceof TextMessage textMessage)) {
            System.out.println("Mensaje ignorado en topic " + topicName + ": no es TextMessage.");
            return;
        }

        try {
            String eventJson = textMessage.getText();
            JsonNode event = objectMapper.readTree(eventJson);
            JsonNode payload = event.get("payload");
            String ts = text(event, "ts");
            String ss = text(event, "ss");

            if (isBlank(ts) || isBlank(ss) || payload == null || !payload.isObject()) {
                System.out.println("Evento ignorado en topic " + topicName + ": faltan ts, ss o payload.");
                return;
            }

            if (EventTopics.AWAY_MATCH.equals(topicName)) {
                if (!hasAwayMatchKey(payload, ss)) {
                    System.out.println("Evento AwayMatch ignorado: faltan claves de datamart.");
                    return;
                }
                saveAwayMatch(payload, ts, ss);
                System.out.println("Evento AwayMatch guardado en datamart desde ActiveMQ.");
            } else if (EventTopics.FLIGHT_INFO.equals(topicName)) {
                if (!hasFlightInfoKey(payload, ss)) {
                    System.out.println("Evento FlightInfo ignorado: faltan claves de datamart.");
                    return;
                }
                saveFlightInfo(payload, ts, ss);
                System.out.println("Evento FlightInfo guardado en datamart desde ActiveMQ.");
            }
        } catch (JMSException | IOException | SQLException e) {
            System.out.println("No se pudo procesar evento ActiveMQ: " + e.getMessage());
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

    private void closeResources() {
        closeConsumers();
        closeSession();
        closeConnection();
    }

    private void closeConsumers() {
        for (MessageConsumer consumer : consumers) {
            try {
                consumer.close();
            } catch (JMSException e) {
                System.out.println("No se pudo cerrar un consumer de ActiveMQ: " + e.getMessage());
            }
        }
        consumers.clear();
    }

    private void closeSession() {
        if (session == null) {
            return;
        }

        try {
            session.close();
        } catch (JMSException e) {
            System.out.println("No se pudo cerrar la sesion de ActiveMQ: " + e.getMessage());
        } finally {
            session = null;
        }
    }

    private void closeConnection() {
        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (JMSException e) {
            System.out.println("No se pudo cerrar la conexion de ActiveMQ: " + e.getMessage());
        } finally {
            connection = null;
        }
    }
}
