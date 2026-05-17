package org.ulpgc.dacd.domain;

import java.time.LocalDateTime;

public class FlightInfo {
    private String flightNumber;
    private String airline;
    private String originAirport;
    private String destinationAirport;
    private String scheduledDateTime;
    private String status;
    private String terminal;
    private String source;
    private LocalDateTime capturedAt;

    public FlightInfo() {
    }

    public FlightInfo(String flightNumber, String airline, String originAirport,
                      String destinationAirport, String scheduledDateTime, String status,
                      String terminal, String source, LocalDateTime capturedAt) {
        this.flightNumber = flightNumber;
        this.airline = airline;
        this.originAirport = originAirport;
        this.destinationAirport = destinationAirport;
        this.scheduledDateTime = scheduledDateTime;
        this.status = status;
        this.terminal = terminal;
        this.source = source;
        this.capturedAt = capturedAt;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public String getAirline() {
        return airline;
    }

    public String getOriginAirport() {
        return originAirport;
    }

    public String getDestinationAirport() {
        return destinationAirport;
    }

    public String getScheduledDateTime() {
        return scheduledDateTime;
    }

    public String getStatus() {
        return status;
    }

    public String getTerminal() {
        return terminal;
    }

    public String getSource() {
        return source;
    }

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }

    @Override
    public String toString() {
        return "FlightInfo{" +
                "flightNumber='" + flightNumber + '\'' +
                ", airline='" + airline + '\'' +
                ", originAirport='" + originAirport + '\'' +
                ", destinationAirport='" + destinationAirport + '\'' +
                ", scheduledDateTime='" + scheduledDateTime + '\'' +
                ", status='" + status + '\'' +
                ", terminal='" + terminal + '\'' +
                ", source='" + source + '\'' +
                ", capturedAt=" + capturedAt +
                '}';
    }
}
