package org.ulpgc.dacd.app;

import org.ulpgc.dacd.domain.AirportMapping;
import org.ulpgc.dacd.matches.AwayMatchRepository;
import org.ulpgc.dacd.matches.AwayMatchService;
import org.ulpgc.dacd.matches.LaligaMatchScraper;

import java.sql.SQLException;

public class Main {
    public static void main(String[] args) {
        DatabaseInitializer databaseInitializer = new DatabaseInitializer();
        AwayMatchService awayMatchService = new AwayMatchService(
                new LaligaMatchScraper(),
                new AirportMapping(),
                new AwayMatchRepository()
        );

        try {
            System.out.println("Inicializando base de datos SQLite...");
            databaseInitializer.initializeDatabase();
            System.out.println("Base de datos SQLite preparada correctamente");
            System.out.println("Obteniendo proximos partidos de la UD Las Palmas desde laliga.com...");
            awayMatchService.captureAwayMatches();
            System.out.println("PioPioFly iniciado correctamente");
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());
        } catch (SQLException e) {
            System.out.println("No se pudo preparar la base de datos SQLite o guardar partidos: " + e.getMessage());
        }
    }
}
