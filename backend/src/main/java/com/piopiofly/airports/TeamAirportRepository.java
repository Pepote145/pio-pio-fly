package com.piopiofly.airports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TeamAirportRepository extends JpaRepository<TeamAirport, Long> {

    List<TeamAirport> findByTeamSlugInOrderByPriorityAsc(Collection<String> teamSlugs);
}
