package com.aneto.registo_horas_service.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FootballData {

    public ResultSet resultSet;
    public List<Match> matches;
    public Filters filters; // Adicionado para mapear o topo do JSON

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Filters {
        public String dateFrom;
        public String dateTo;
        public String permission;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResultSet {
        public int count;
        public String competitions;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Match {
        public int id;
        public String utcDate;
        public String status;
        public Competition competition;
        public Team homeTeam;
        public Team awayTeam;
        public Score score;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Competition {
        public String name;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Team {
        public String name;
        public String shortName;
        public String crest;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Score {
        public String winner;
        public FullTime fullTime;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FullTime {
        public Integer home;
        public Integer away;
    }
}