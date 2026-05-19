package org.ulpgc.dacd.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

public class ActiveMqEventPublisher implements EventPublisher {
    public static final String DEFAULT_BROKER_URL = "tcp://localhost:61616";

    private final ObjectMapper objectMapper;
    private final Connection connection;
    private final Session session;
    private boolean closed;

    public ActiveMqEventPublisher() {
        this(DEFAULT_BROKER_URL);
    }

    public ActiveMqEventPublisher(String brokerUrl) {
        this(brokerUrl, new ObjectMapper());
    }

    public ActiveMqEventPublisher(String brokerUrl, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        Connection createdConnection = null;
        Session createdSession = null;
        try {
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
            createdConnection = connectionFactory.createConnection();
            createdSession = createdConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            createdConnection.start();
        } catch (JMSException e) {
            closeQuietly(createdSession, "sesion");
            closeQuietly(createdConnection, "conexion");
            throw new IllegalStateException("No se pudo conectar con ActiveMQ en " + brokerUrl, e);
        }
        this.connection = createdConnection;
        this.session = createdSession;
    }

    @Override
    public void publish(String topicName, EventMessage eventMessage) {
        if (closed) {
            throw new IllegalStateException("No se puede publicar porque el publisher ActiveMQ esta cerrado");
        }

        MessageProducer producer = null;
        try {
            Destination destination = session.createTopic(topicName);
            producer = session.createProducer(destination);
            TextMessage message = session.createTextMessage(objectMapper.writeValueAsString(eventMessage));
            producer.send(message);
        } catch (JMSException | JsonProcessingException e) {
            throw new IllegalStateException("No se pudo publicar el evento en el topic " + topicName, e);
        } finally {
            closeProducer(producer);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closeQuietly(session, "sesion");
        closeQuietly(connection, "conexion");
        closed = true;
    }

    private void closeProducer(MessageProducer producer) {
        if (producer == null) {
            return;
        }

        try {
            producer.close();
        } catch (JMSException e) {
            System.out.println("No se pudo cerrar el productor de ActiveMQ: " + e.getMessage());
        }
    }

    private void closeQuietly(Session session, String resourceName) {
        if (session == null) {
            return;
        }

        try {
            session.close();
        } catch (JMSException e) {
            System.out.println("No se pudo cerrar " + resourceName + " de ActiveMQ: " + e.getMessage());
        }
    }

    private void closeQuietly(Connection connection, String resourceName) {
        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (JMSException e) {
            System.out.println("No se pudo cerrar " + resourceName + " de ActiveMQ: " + e.getMessage());
        }
    }
}
