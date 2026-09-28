package com.piopiofly.matches;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AwayMatchRepository extends JpaRepository<AwayMatch, Long> {

    Optional<AwayMatch> findByProviderAndExternalId(String provider, String externalId);

    List<AwayMatch> findByMatchDateGreaterThanEqualOrderByMatchDateAsc(LocalDate from);
}
