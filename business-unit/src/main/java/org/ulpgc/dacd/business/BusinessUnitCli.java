package org.ulpgc.dacd.business;

import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

public class BusinessUnitCli {
    private static final String PENDING_MESSAGE = "Funcionalidad pendiente de implementar en el siguiente bloque.";

    private final DatamartRepository datamartRepository;
    private final EventStoreDatamartLoader eventStoreDatamartLoader;
    private final BusinessUnitEventSubscriber eventSubscriber;

    public BusinessUnitCli(DatamartRepository datamartRepository,
                           EventStoreDatamartLoader eventStoreDatamartLoader,
                           BusinessUnitEventSubscriber eventSubscriber) {
        this.datamartRepository = datamartRepository;
        this.eventStoreDatamartLoader = eventStoreDatamartLoader;
        this.eventSubscriber = eventSubscriber;
    }

    public void start() {
        try (Scanner scanner = new Scanner(System.in)) {
            boolean running = true;
            while (running) {
                printMenu();
                String option = scanner.nextLine().trim();
                running = handleOption(option, scanner);
            }
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("=== PioPioFly Business Unit ===");
        System.out.println("1. Ver proximos partidos fuera de casa");
        System.out.println("2. Ver vuelos disponibles por destino");
        System.out.println("3. Ver recomendacion de desplazamiento");
        System.out.println("4. Ver resumen del datamart");
        System.out.println("5. Recargar historicos desde eventstore");
        System.out.println("6. Iniciar sincronizacion en vivo desde ActiveMQ");
        System.out.println("7. Detener sincronizacion en vivo");
        System.out.println("0. Salir");
        System.out.print("Selecciona una opcion: ");
    }

    private boolean handleOption(String option, Scanner scanner) {
        switch (option) {
            case "1" -> {
                showUpcomingAwayMatches();
                return true;
            }
            case "2" -> {
                showFlightsByDestination(scanner);
                return true;
            }
            case "3" -> {
                showTravelRecommendations();
                return true;
            }
            case "4" -> {
                showDatamartSummary();
                return true;
            }
            case "5" -> {
                reloadEventStoreHistory();
                return true;
            }
            case "6" -> {
                eventSubscriber.start();
                return true;
            }
            case "7" -> {
                eventSubscriber.stop();
                return true;
            }
            case "0" -> {
                if (eventSubscriber.isActive()) {
                    eventSubscriber.stop();
                }
                System.out.println("Cerrando PioPioFly Business Unit.");
                return false;
            }
            default -> {
                System.out.println("Opcion no valida.");
                return true;
            }
        }
    }

    private void showDatamartSummary() {
        try {
            DatamartSummary summary = datamartRepository.getSummary();
            System.out.println();
            System.out.println("Resumen del datamart:");
            System.out.println("- Partidos fuera de casa: " + summary.awayMatches());
            System.out.println("- Vuelos disponibles: " + summary.flights());
            System.out.println("- Destinos registrados: " + summary.destinations());
            System.out.println("- Fuentes registradas: " + summary.sources());
        } catch (SQLException e) {
            System.out.println("No se pudo leer el resumen del datamart: " + e.getMessage());
        }
    }

    private void showUpcomingAwayMatches() {
        try {
            List<AwayMatchView> matches = datamartRepository.findUpcomingAwayMatches();
            if (matches.isEmpty()) {
                System.out.println("No hay partidos cargados en el datamart. Usa primero la opcion 5.");
                return;
            }

            System.out.println();
            System.out.println("Proximos partidos fuera de casa:");
            for (AwayMatchView match : matches) {
                System.out.println("- " + display(match.matchDate())
                        + " | " + display(match.homeTeam()) + " vs " + display(match.awayTeam())
                        + " | Ciudad: " + display(match.city())
                        + " | Estadio: " + display(match.stadium())
                        + " | Aeropuerto destino: " + display(match.destinationAirport()));
            }
        } catch (SQLException e) {
            System.out.println("No se pudieron consultar partidos del datamart: " + e.getMessage());
        }
    }

    private void showFlightsByDestination(Scanner scanner) {
        try {
            List<String> destinations = datamartRepository.findAvailableDestinations();
            if (destinations.isEmpty()) {
                System.out.println("No hay vuelos cargados en el datamart. Usa primero la opcion 5.");
                return;
            }

            System.out.println();
            System.out.println("Destinos disponibles: " + String.join(", ", destinations));
            System.out.print("Introduce codigo de aeropuerto destino: ");
            String destinationAirport = scanner.nextLine().trim().toUpperCase();

            List<FlightInfoView> flights = datamartRepository.findFlightsByDestination(destinationAirport);
            if (flights.isEmpty()) {
                System.out.println("No hay vuelos cargados para ese destino.");
                return;
            }

            System.out.println();
            System.out.println("Vuelos hacia " + destinationAirport + ":");
            for (FlightInfoView flight : flights) {
                System.out.println("- " + display(flight.scheduledDateTime())
                        + " | Vuelo: " + display(flight.flightNumber())
                        + " | Aerolinea: " + display(flight.airline())
                        + " | " + display(flight.originAirport()) + " -> " + display(flight.destinationAirport())
                        + " | Estado: " + display(flight.status())
                        + " | Terminal: " + display(flight.terminal()));
            }
        } catch (SQLException e) {
            System.out.println("No se pudieron consultar vuelos del datamart: " + e.getMessage());
        }
    }

    private void showTravelRecommendations() {
        try {
            List<TravelRecommendation> recommendations = datamartRepository.buildTravelRecommendations();
            if (recommendations.isEmpty()) {
                System.out.println("No hay partidos cargados en el datamart. Usa primero la opcion 5.");
                return;
            }

            System.out.println();
            System.out.println("Recomendaciones de desplazamiento:");
            for (TravelRecommendation recommendation : recommendations) {
                AwayMatchView match = recommendation.match();
                System.out.println();
                System.out.println(display(match.homeTeam()) + " vs " + display(match.awayTeam())
                        + " | Fecha: " + display(match.matchDate()));
                System.out.println("- Ciudad: " + display(match.city()));
                System.out.println("- Estadio: " + display(match.stadium()));
                System.out.println("- Aeropuerto destino: " + display(recommendation.destinationAirport()));
                System.out.println("- Nivel de recomendacion: " + recommendation.level());
                System.out.println("- Vuelos encontrados: " + recommendation.availableFlights());
                if (recommendation.suggestedFlight() == null) {
                    System.out.println("- No hay vuelos cargados para ese destino.");
                } else {
                    FlightInfoView flight = recommendation.suggestedFlight();
                    System.out.println("- Vuelo sugerido:");
                    System.out.println("  Fecha/hora: " + display(flight.scheduledDateTime()));
                    System.out.println("  Numero de vuelo: " + display(flight.flightNumber()));
                    System.out.println("  Aerolinea: " + display(flight.airline()));
                    System.out.println("  Ruta: " + display(flight.originAirport()) + " -> "
                            + display(flight.destinationAirport()));
                    System.out.println("  Estado: " + display(flight.status()));
                    System.out.println("  Terminal: " + display(flight.terminal()));
                }
                System.out.println("- Motivo: " + recommendation.reason());
            }
        } catch (SQLException e) {
            System.out.println("No se pudieron generar recomendaciones: " + e.getMessage());
        }
    }

    private void reloadEventStoreHistory() {
        DatamartLoadResult result = eventStoreDatamartLoader.load();
        System.out.println();
        System.out.println("Recarga de historicos finalizada:");
        System.out.println("- Eventos procesados: " + result.processedEvents());
        System.out.println("- Partidos cargados: " + result.loadedAwayMatches());
        System.out.println("- Vuelos cargados: " + result.loadedFlights());
        System.out.println("- Eventos omitidos: " + result.skippedEvents());
        System.out.println("- Eventos con error: " + result.failedEvents());
    }

    private String display(String value) {
        return value == null || value.isBlank() ? "N/D" : value;
    }
}
