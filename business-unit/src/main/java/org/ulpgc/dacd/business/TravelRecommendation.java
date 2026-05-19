package org.ulpgc.dacd.business;

public record TravelRecommendation(
        AwayMatchView match,
        String destinationAirport,
        int availableFlights,
        FlightInfoView suggestedFlight,
        String reason,
        RecommendationLevel level
) {
    public enum RecommendationLevel {
        ALTA,
        MEDIA,
        BAJA,
        SIN_VUELOS
    }
}
