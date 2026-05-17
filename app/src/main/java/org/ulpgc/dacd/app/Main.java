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
            System.out.println("Obteniendo proximos partidos fuera de la UD Las Palmas desde laliga.com...");
            awayMatchService.captureAwayMatches();
            System.out.println("Captura de partidos finalizada");
            System.out.println("Enlace oficial de entradas: " + ticketLinkProvider.getOfficialAwayTicketUrl());
            System.out.println("Obteniendo vuelos desde AENA...");
            flightInfoService.captureFlightsForAwayMatches();
            System.out.println("Captura de vuelos AENA finalizada");
            System.out.println("PioPioFly iniciado correctamente");
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());
        } catch (SQLException e) {
            System.out.println("No se pudo preparar la base de datos SQLite o guardar datos: " + e.getMessage());
        }
    }
}
