package org.ulpgc.dacd.flights;

import org.ulpgc.dacd.domain.FlightInfo;

import java.util.List;

public interface FlightInfoScraper {
    List<FlightInfo> fetchFlights(String originAirport, String destinationAirport, String date);
}
