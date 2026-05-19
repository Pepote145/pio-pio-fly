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

    public ActiveMqEventPublisher() {
        this(DEFAULT_BROKER_URL);
    }

    public ActiveMqEventPublisher(String brokerUrl) {
        this(brokerUrl, new ObjectMapper());
    }

    public ActiveMqEventPublisher(String brokerUrl, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        try {
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
            this.connection = connectionFactory.createConnection();
            this.connection.start();
            this.session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        } catch (JMSException e) {
            throw new IllegalStateException("No se pudo conectar con ActiveMQ en " + brokerUrl, e);
        }
    }

    @Override
    public void publish(String topicName, EventMessage eventMessage) {
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
        try {
            session.close();
            connection.close();
        } catch (JMSException e) {
            throw new IllegalStateException("No se pudo cerrar la conexion con ActiveMQ", e);
        }
    }

    private void closeProducer(MessageProducer producer) {
        if (producer == null) {
            return;
        }

        try {
            producer.close();
        } catch (JMSException e) {
            throw new IllegalStateException("No se pudo cerrar el productor de ActiveMQ", e);
        }
    }
}
