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
    private static final long RECONNECT_DELAY_MILLIS = 5000;

    private final String brokerUrl;
    private final String clientId;
    private final List<String> topics;
    private final List<MessageConsumer> consumers;
    private final EventStoreWriter eventStoreWriter;
    private final Object lifecycleLock;
    private final Object jmsLock;

    private Connection connection;
    private Session session;
    private Thread workerThread;
    private volatile boolean closed;
    private boolean reconnectRequested;

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
        this.lifecycleLock = new Object();
        this.jmsLock = new Object();
    }

    public void start() {
        synchronized (lifecycleLock) {
            if (workerThread != null) {
                return;
            }

            workerThread = new Thread(this::runSubscriber, "pio-pio-fly-event-store-subscriber");
            workerThread.start();
        }
    }

    @Override
    public void close() {
        Thread threadToInterrupt;
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }

            closed = true;
            reconnectRequested = true;
            lifecycleLock.notifyAll();
            threadToInterrupt = workerThread;
        }

        if (threadToInterrupt != null) {
            threadToInterrupt.interrupt();
        }

        closeJmsResources();
    }

    private void runSubscriber() {
        while (!closed) {
            try {
                System.out.println("Intentando conectar con ActiveMQ en " + brokerUrl + "...");
                connectAndSubscribe();
                System.out.println("Event Store Builder conectado a ActiveMQ.");
                waitUntilReconnectIsNeeded();
            } catch (JMSException e) {
                if (!closed) {
                    System.out.println("ActiveMQ no disponible o conexion perdida: " + e.getMessage());
                }
            } finally {
                closeJmsResources();
            }

            waitBeforeReconnect();
        }

        System.out.println("Subscriber ActiveMQ detenido.");
    }

    private void connectAndSubscribe() throws JMSException {
        synchronized (jmsLock) {
            if (closed) {
                return;
            }

            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
            connection = connectionFactory.createConnection();
            connection.setClientID(clientId);
            connection.setExceptionListener(this::handleConnectionException);
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            for (String topicName : topics) {
                createDurableSubscription(topicName);
            }

            connection.start();
            System.out.println("Suscripciones durables iniciadas en ActiveMQ.");
        }
    }

    private void handleConnectionException(JMSException exception) {
        if (closed) {
            return;
        }

        synchronized (lifecycleLock) {
            if (!reconnectRequested) {
                System.out.println("Se perdio la conexion con ActiveMQ: " + exception.getMessage());
            }
            reconnectRequested = true;
            lifecycleLock.notifyAll();
        }
    }

    private void waitUntilReconnectIsNeeded() {
        synchronized (lifecycleLock) {
            while (!closed && !reconnectRequested) {
                try {
                    lifecycleLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    closed = true;
                    return;
                }
            }

            reconnectRequested = false;
        }
    }

    private void waitBeforeReconnect() {
        if (closed) {
            return;
        }

        System.out.println("Reintentando conexion con ActiveMQ en "
                + (RECONNECT_DELAY_MILLIS / 1000) + " segundos.");

        synchronized (lifecycleLock) {
            try {
                lifecycleLock.wait(RECONNECT_DELAY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                closed = true;
            }
        }
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
        } catch (RuntimeException e) {
            System.out.println("No se pudo guardar evento del topic " + topicName + ": " + e.getMessage());
        }
    }

    private void closeJmsResources() {
        synchronized (jmsLock) {
            closeConsumers();
            closeSession();
            closeConnection();
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
