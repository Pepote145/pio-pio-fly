package org.ulpgc.dacd.flights;

import org.ulpgc.dacd.domain.FlightInfo;

import java.time.LocalDate;
import java.util.List;

public class AenaLcgDebugRunner {
    public static void main(String[] args) {
        AenaFlightScraper scraper = new AenaFlightScraper();
        runQuery(scraper, "2026-05-29");
        runQuery(scraper, "2026-05-30");
    }

    private static void runQuery(AenaFlightScraper scraper, String date) {
        System.out.println();
        System.out.println("=== Diagnostico AENA LPA -> LCG " + date + " ===");
        List<FlightInfo> flights = scraper.scrapeFlights("LPA", "LCG", LocalDate.parse(date));
        System.out.println("Vuelos encontrados: " + flights.size());
        for (FlightInfo flight : flights) {
            System.out.println("- " + flight);
        }
    }
}
