package org.ulpgc.dacd.app;

import java.sql.SQLException;

public class Main {
    public static void main(String[] args) {
        DatabaseInitializer databaseInitializer = new DatabaseInitializer();

        try {
            databaseInitializer.initializeDatabase();
            System.out.println("Base de datos SQLite preparada correctamente");
            System.out.println("PioPioFly iniciado correctamente");
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo preparar la base de datos SQLite", e);
        }
    }
}
