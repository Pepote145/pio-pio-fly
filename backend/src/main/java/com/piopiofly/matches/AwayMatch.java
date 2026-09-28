package com.piopiofly.matches;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "away_match")
public class AwayMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String provider;

    @Column(nullable = false, updatable = false)
    private String externalId;

    @Column(nullable = false)
    private String competition;

    private String season;

    private Integer gameweek;

    @Column(nullable = false)
    private String homeTeamSlug;

    @Column(nullable = false)
    private String homeTeamName;

    @Column(nullable = false)
    private LocalDate matchDate;

    private Instant kickoffAt;

    private String stadium;

    private String city;

    private Double latitude;

    private Double longitude;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected AwayMatch() {
    }

    public AwayMatch(String provider, String externalId) {
        this.provider = provider;
        this.externalId = externalId;
    }

    /** Copia los datos de la fuente; la identidad (fuente + id externo) no cambia nunca. */
    public void updateFrom(Fixture fixture) {
        this.competition = fixture.competition();
        this.season = fixture.season();
        this.gameweek = fixture.gameweek();
        this.homeTeamSlug = fixture.homeTeam().slug();
        this.homeTeamName = fixture.homeTeam().name();
        this.matchDate = fixture.matchDate();
        this.kickoffAt = fixture.kickoffAt();
        this.stadium = fixture.stadium();
        this.city = fixture.city();
        this.latitude = fixture.latitude();
        this.longitude = fixture.longitude();
    }

    public DateStatus dateStatus() {
        return kickoffAt == null ? DateStatus.PROVISIONAL : DateStatus.CONFIRMED;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getCompetition() {
        return competition;
    }

    public String getSeason() {
        return season;
    }

    public Integer getGameweek() {
        return gameweek;
    }

    public String getHomeTeamSlug() {
        return homeTeamSlug;
    }

    public String getHomeTeamName() {
        return homeTeamName;
    }

    public LocalDate getMatchDate() {
        return matchDate;
    }

    public Instant getKickoffAt() {
        return kickoffAt;
    }

    public String getStadium() {
        return stadium;
    }

    public String getCity() {
        return city;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }
}
