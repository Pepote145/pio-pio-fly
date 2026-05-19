package org.ulpgc.dacd.eventstore;

import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.util.ArrayList;
import java.util.List;

public class ActiveMqEventStoreSubscriber implements AutoCloseable {
    private final String brokerUrl;
    private final String clientId;
    private final List<String> topics;
    private final List<MessageConsumer> consumers;
    private final EventStoreWriter eventStoreWriter;

    private Connection connection;
    private Session session;

    public ActiveMqEventStoreSubscriber(String brokerUrl, String clientId, List<String> topics) {
        this(brokerUrl, clientId, topics, new EventStoreWriter("eventstore"));
    }

    public ActiveMqEventStoreSubscriber(String brokerUrl, String clientId, List<String> topics,
                                        EventStoreWriter eventStoreWriter) {
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
        this.topics = topics;
        this.consumers = new ArrayList<>();
        this.eventStoreWriter = eventStoreWriter;
    }

    public void start() {
        try {
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
            connection = connectionFactory.createConnection();
            connection.setClientID(clientId);
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            for (String topicName : topics) {
                createDurableSubscription(topicName);
            }

            connection.start();
            System.out.println("Suscripciones durables iniciadas en ActiveMQ.");
        } catch (JMSException e) {
            close();
            throw new IllegalStateException("No se pudo conectar o suscribir a ActiveMQ en "
                    + brokerUrl + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        closeConsumers();
        closeSession();
        closeConnection();
    }

    private void createDurableSubscription(String topicName) throws JMSException {
        Topic topic = session.createTopic(topicName);
        MessageConsumer consumer = session.createDurableSubscriber(topic, subscriptionName(topicName));
        consumer.setMessageListener(message -> handleMessage(topicName, message));
        consumers.add(consumer);
        System.out.println("Suscripcion durable preparada: " + subscriptionName(topicName));
    }

    private String subscriptionName(String topicName) {
        return "event-store-builder-" + topicName;
    }

    private void handleMessage(String topicName, Message message) {
        if (!(message instanceof TextMessage textMessage)) {
            System.out.println("Mensaje ignorado en topic " + topicName + ": no es TextMessage.");
            return;
        }

        try {
            System.out.println("Evento recibido en topic " + topicName + ":");
            String eventJson = textMessage.getText();
            System.out.println(eventJson);
            eventStoreWriter.append(topicName, eventJson);
        } catch (JMSException e) {
            System.out.println("No se pudo leer mensaje del topic " + topicName + ": " + e.getMessage());
        }
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
        }
    }
}
