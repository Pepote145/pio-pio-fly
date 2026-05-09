package org.ulpgc.dacd.domain;

import java.time.LocalDateTime;

public class Match {
    private String externalId;
    private String competition;
    private String homeTeam;
    private String awayTeam;
    private LocalDateTime matchDate;
    private String city;
    private String stadium;
    private String destinationAirport;
    private String source;
    private LocalDateTime capturedAt;

    public Match() {
    }

    public Match(String externalId, String competition, String homeTeam, String awayTeam,
                 LocalDateTime matchDate, String city, String stadium, String destinationAirport,
                 String source, LocalDateTime capturedAt) {
        this.externalId = externalId;
        this.competition = competition;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.matchDate = matchDate;
        this.city = city;
        this.stadium = stadium;
        this.destinationAirport = destinationAirport;
        this.source = source;
        this.capturedAt = capturedAt;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getCompetition() {
        return competition;
    }

    public String getHomeTeam() {
        return homeTeam;
    }

    public String getAwayTeam() {
        return awayTeam;
    }

    public LocalDateTime getMatchDate() {
        return matchDate;
    }

    public String getCity() {
        return city;
    }

    public String getStadium() {
        return stadium;
    }

    public String getDestinationAirport() {
        return destinationAirport;
    }

    public String getSource() {
        return source;
    }

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }

    @Override
    public String toString() {
        return "Match{" +
                "externalId='" + externalId + '\'' +
                ", competition='" + competition + '\'' +
                ", homeTeam='" + homeTeam + '\'' +
                ", awayTeam='" + awayTeam + '\'' +
                ", matchDate=" + matchDate +
                ", city='" + city + '\'' +
                ", stadium='" + stadium + '\'' +
                ", destinationAirport='" + destinationAirport + '\'' +
                ", source='" + source + '\'' +
                ", capturedAt=" + capturedAt +
                '}';
    }
}
