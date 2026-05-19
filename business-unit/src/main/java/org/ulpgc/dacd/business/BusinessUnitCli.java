package org.ulpgc.dacd.business;

import java.sql.SQLException;
import java.util.Scanner;

public class BusinessUnitCli {
    private static final String PENDING_MESSAGE = "Funcionalidad pendiente de implementar en el siguiente bloque.";

    private final DatamartRepository datamartRepository;
    private final EventStoreDatamartLoader eventStoreDatamartLoader;

    public BusinessUnitCli(DatamartRepository datamartRepository,
                           EventStoreDatamartLoader eventStoreDatamartLoader) {
        this.datamartRepository = datamartRepository;
        this.eventStoreDatamartLoader = eventStoreDatamartLoader;
    }

    public void start() {
        try (Scanner scanner = new Scanner(System.in)) {
            boolean running = true;
            while (running) {
                printMenu();
                String option = scanner.nextLine().trim();
                running = handleOption(option);
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
        System.out.println("0. Salir");
        System.out.print("Selecciona una opcion: ");
    }

    private boolean handleOption(String option) {
        switch (option) {
            case "1", "2", "3" -> {
                System.out.println(PENDING_MESSAGE);
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
            case "0" -> {
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
}
