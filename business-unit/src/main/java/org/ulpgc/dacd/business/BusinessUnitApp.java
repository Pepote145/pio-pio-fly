package org.ulpgc.dacd.business;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;

public class BusinessUnitApp {
    public static void main(String[] args) {
        System.out.println("PioPioFly Business Unit iniciada");
        System.out.println("Broker ActiveMQ: " + BusinessUnitConfig.BROKER_URL);
        System.out.println("Datamart: " + BusinessUnitConfig.DATAMART_DATABASE_URL);
        System.out.println("Event store: " + BusinessUnitConfig.EVENT_STORE_BASE_PATH);

        try {
            DatamartInitializer datamartInitializer = new DatamartInitializer();
            datamartInitializer.initialize();
            System.out.println("Datamart preparado correctamente.");
        } catch (SQLException e) {
            System.out.println("No se pudo inicializar el datamart: " + e.getMessage());
            return;
        }

        DatamartRepository datamartRepository = new DatamartRepository();
        EventStoreDatamartLoader eventStoreDatamartLoader = new EventStoreDatamartLoader(datamartRepository);

        System.out.println("Recargando historicos desde eventstore...");
        DatamartLoadResult loadResult = eventStoreDatamartLoader.load();
        System.out.println("Eventos procesados: " + loadResult.processedEvents());
        System.out.println("Partidos cargados: " + loadResult.loadedAwayMatches());
        System.out.println("Vuelos cargados: " + loadResult.loadedFlights());
        if (loadResult.skippedEvents() > 0 || loadResult.failedEvents() > 0) {
            System.out.println("Eventos omitidos: " + loadResult.skippedEvents()
                    + " | Eventos con error: " + loadResult.failedEvents());
        }

        BusinessUnitEventSubscriber eventSubscriber = new BusinessUnitEventSubscriber(
                BusinessUnitConfig.BROKER_URL,
                BusinessUnitConfig.CLIENT_ID,
                datamartRepository
        );
        BusinessUnitWebServer webServer = new BusinessUnitWebServer(
                datamartRepository,
                eventStoreDatamartLoader,
                eventSubscriber,
                BusinessUnitConfig.webPort
        );
        CountDownLatch keepAlive = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            webServer.stop();
            if (eventSubscriber.isActive()) {
                eventSubscriber.stop();
            }
            keepAlive.countDown();
        }));

        try {
            boolean liveSyncStarted = eventSubscriber.start();
            if (liveSyncStarted) {
                System.out.println("Sincronizacion en vivo ActiveMQ iniciada");
            }
            webServer.start();
            System.out.println("PioPioFly Business Unit Web disponible en http://localhost:" + BusinessUnitConfig.webPort);
            keepAlive.await();
        } catch (IOException e) {
            System.out.println("No se pudo iniciar la web de Business Unit: " + e.getMessage());
            if (eventSubscriber.isActive()) {
                eventSubscriber.stop();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Business Unit interrumpida.");
        }
    }
}
