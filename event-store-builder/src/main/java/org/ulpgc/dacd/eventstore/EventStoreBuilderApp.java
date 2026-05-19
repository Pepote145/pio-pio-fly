package org.ulpgc.dacd.eventstore;

import org.ulpgc.dacd.domain.ActiveMqEventPublisher;
import org.ulpgc.dacd.domain.EventTopics;

import java.util.concurrent.CountDownLatch;
import java.util.List;

public class EventStoreBuilderApp {
    public static final String DEFAULT_BROKER_URL = ActiveMqEventPublisher.DEFAULT_BROKER_URL;
    public static final String DURABLE_CLIENT_ID = "pio-pio-fly-event-store-builder";
    public static final String EVENT_STORE_BASE_DIRECTORY = "eventstore";
    public static final List<String> TOPICS = List.of(
            EventTopics.AWAY_MATCH,
            EventTopics.FLIGHT_INFO
    );

    public static void main(String[] args) {
        System.out.println("Event Store Builder iniciado");
        System.out.println("Broker ActiveMQ: " + DEFAULT_BROKER_URL);
        System.out.println("ClientId durable: " + DURABLE_CLIENT_ID);
        System.out.println("Topics configurados: " + TOPICS);
        System.out.println("Directorio base del event store: " + EVENT_STORE_BASE_DIRECTORY);

        CountDownLatch shutdownLatch = new CountDownLatch(1);
        EventStoreWriter eventStoreWriter = new EventStoreWriter(EVENT_STORE_BASE_DIRECTORY);
        ActiveMqEventStoreSubscriber subscriber = new ActiveMqEventStoreSubscriber(
                DEFAULT_BROKER_URL,
                DURABLE_CLIENT_ID,
                TOPICS,
                eventStoreWriter
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Cerrando Event Store Builder...");
            subscriber.close();
            shutdownLatch.countDown();
        }));

        try {
            subscriber.start();
            System.out.println("Event Store Builder en ejecucion. Pulsa Ctrl+C para detener.");
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            subscriber.close();
            System.out.println("Event Store Builder interrumpido.");
        }
    }
}
