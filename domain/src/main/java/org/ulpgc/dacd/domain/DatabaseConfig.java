package org.ulpgc.dacd.domain;

public final class DatabaseConfig {
    public static final String DATABASE_NAME = "pio_pio_fly.db";
    public static final String DATABASE_URL = "jdbc:sqlite:" + DATABASE_NAME;

    private DatabaseConfig() {
    }
}
