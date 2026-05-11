package org.ulpgc.dacd.matches;

import org.ulpgc.dacd.domain.Match;

import java.util.List;

public interface MatchClient {
    List<Match> fetchMatches();
}
