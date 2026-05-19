package org.ulpgc.dacd.business;

import java.sql.SQLException;

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
        BusinessUnitEventSubscriber eventSubscriber = new BusinessUnitEventSubscriber(
                BusinessUnitConfig.BROKER_URL,
                BusinessUnitConfig.CLIENT_ID,
                datamartRepository
        );
        BusinessUnitCli cli = new BusinessUnitCli(datamartRepository, eventStoreDatamartLoader, eventSubscriber);
        cli.start();
    }
}
