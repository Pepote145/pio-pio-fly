package org.ulpgc.dacd.business;

import java.util.Scanner;

public class BusinessUnitCli {
    private static final String PENDING_MESSAGE = "Funcionalidad pendiente de implementar en el siguiente bloque.";

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
            case "1", "2", "3", "4", "5" -> {
                System.out.println(PENDING_MESSAGE);
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
}
