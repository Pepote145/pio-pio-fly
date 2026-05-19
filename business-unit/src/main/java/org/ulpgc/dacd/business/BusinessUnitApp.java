package org.ulpgc.dacd.business;

public class BusinessUnitApp {
    public static void main(String[] args) {
        System.out.println("PioPioFly Business Unit iniciada");
        System.out.println("Broker ActiveMQ: " + BusinessUnitConfig.BROKER_URL);
        System.out.println("Datamart: " + BusinessUnitConfig.DATAMART_DATABASE_URL);
        System.out.println("Event store: " + BusinessUnitConfig.EVENT_STORE_BASE_PATH);

        BusinessUnitCli cli = new BusinessUnitCli();
        cli.start();
    }
}
