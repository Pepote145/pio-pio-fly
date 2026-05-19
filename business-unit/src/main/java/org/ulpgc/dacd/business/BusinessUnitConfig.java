package org.ulpgc.dacd.business;

public final class BusinessUnitConfig {
    public static final String BROKER_URL = "tcp://localhost:61616";
    public static final String CLIENT_ID = "pio-pio-fly-business-unit";
    public static final String DATAMART_DATABASE_URL = "jdbc:sqlite:business_unit.db";
    public static final String EVENT_STORE_BASE_PATH = "eventstore";

    private BusinessUnitConfig() {
    }
}
