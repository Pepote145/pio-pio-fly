package com.piopiofly.airports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Aeropuerto al que volar para jugar en el campo de un equipo. */
@Entity
@Table(name = "team_airport")
public class TeamAirport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String teamSlug;

    @Column(name = "airport_iata", nullable = false)
    private String iata;

    /** 1 = aeropuerto recomendado. */
    @Column(nullable = false)
    private Integer priority;

    private String note;

    protected TeamAirport() {
    }

    public String getTeamSlug() {
        return teamSlug;
    }

    public String getIata() {
        return iata;
    }

    public Integer getPriority() {
        return priority;
    }

    public String getNote() {
        return note;
    }
}
