package com.aneto.registo_horas_service.mapper;

import com.aneto.registo_horas_service.dto.response.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface FootballMapper {

    default ListJogosResponse toListJogosResponse(FootballData data) {
        if (data == null || data.matches == null) {
            return new ListJogosResponse(List.of());
        }

        List<JogosResponse> todosOsJogos = data.matches.stream()
                .filter(match -> !match.competition.name.equals("Championship"))
                .map(this::toMatchResponse)
                .toList();

        Map<String, List<JogosResponse>> agrupado = todosOsJogos.stream()
                // Agora obtemos os primeiros 10 caracteres da data já formatada (yyyy-MM-dd)
                .collect(Collectors.groupingBy(j -> j.hora().substring(0, 10)));

        List<DiaDeJogoResponse> dias = agrupado.entrySet().stream()
                .map(entry -> new DiaDeJogoResponse(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(DiaDeJogoResponse::data))
                .collect(Collectors.toList());

        return new ListJogosResponse(dias);
    }

    @Mapping(target = "id", source = "id")
    @Mapping(target = "liga", source = "competition.name")
    @Mapping(target = "equipaCasa", source = "homeTeam.shortName")
    @Mapping(target = "equipaFora", source = "awayTeam.shortName")
    @Mapping(target = "hora", source = "utcDate", qualifiedByName = "formatarData") // Chama o formatador
    @Mapping(target = "canal", constant = "Canal não informado")
    @Mapping(target = "iconHome", source = "homeTeam.crest")
    @Mapping(target = "iconAway", source = "awayTeam.crest")
    @Mapping(target = "scoreHome", source = "score.fullTime.home")
    @Mapping(target = "scoreAway", source = "score.fullTime.away")
    JogosResponse toMatchResponse(FootballData.Match match);

    // Método auxiliar para formatar a string da data
    @Named("formatarData")
    default String formatarData(String utcDate) {
        if (utcDate == null) return null;

        // 1. Converte a String ISO (Z) para um objeto ZonedDateTime
        ZonedDateTime dataUtc = ZonedDateTime.parse(utcDate);

        // 2. Define o formato desejado (Espaço em vez de T e sem segundos/fuso)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        return dataUtc.format(formatter);
    }
}