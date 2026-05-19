package org.ulpgc.dacd.app;

import org.ulpgc.dacd.domain.AirportMapping;
import org.ulpgc.dacd.flights.AenaFlightScraper;
import org.ulpgc.dacd.flights.FlightInfoRepository;
import org.ulpgc.dacd.flights.FlightInfoService;
import org.ulpgc.dacd.matches.AwayMatchRepository;
import org.ulpgc.dacd.matches.AwayMatchService;
import org.ulpgc.dacd.matches.LaligaMatchScraper;
import org.ulpgc.dacd.matches.OneboxTicketLinkProvider;

import java.sql.SQLException;

public class Main {
    public static void main(String[] args) {
        DatabaseInitializer databaseInitializer = new DatabaseInitializer();
        AwayMatchService awayMatchService = new AwayMatchService(
                new LaligaMatchScraper(),
                new AirportMapping(),
                new AwayMatchRepository()
        );
        FlightInfoService flightInfoService = new FlightInfoService(
                new AenaFlightScraper(),
                new FlightInfoRepository()
        );
        OneboxTicketLinkProvider ticketLinkProvider = new OneboxTicketLinkProvider();

        try {
            System.out.println("Inicializando base de datos SQLite...");
            databaseInitializer.initializeDatabase();
            System.out.println("Base de datos SQLite preparada correctamente");
        } catch (SQLException e) {
            System.out.println("No se pudo preparar la base de datos SQLite: " + e.getMessage());
            return;
        }

        System.out.println("Obteniendo proximos partidos fuera de la UD Las Palmas desde laliga.com...");
        try {
            awayMatchService.captureAwayMatches();
            System.out.println("Captura de partidos finalizada");
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());
            System.out.println("Captura de partidos finalizada con errores");
        } catch (SQLException e) {
            System.out.println("No se pudieron guardar partidos en SQLite: " + e.getMessage());
        }

        System.out.println("Enlace oficial de entradas: " + ticketLinkProvider.getOfficialAwayTicketUrl());

        System.out.println("Obteniendo vuelos desde AENA...");
        try {
            flightInfoService.captureFlightsForAwayMatches();
            System.out.println("Captura de vuelos AENA finalizada");
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());
        } catch (SQLException e) {
            System.out.println("No se pudieron guardar vuelos en SQLite: " + e.getMessage());
        }

        System.out.println("PioPioFly iniciado correctamente");
    }
}
