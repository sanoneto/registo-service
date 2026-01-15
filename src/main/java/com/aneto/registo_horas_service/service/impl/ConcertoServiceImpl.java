package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.response.ConcertoDTO;
import com.aneto.registo_horas_service.dto.response.PredictHQResponse;
import com.aneto.registo_horas_service.service.ConcertoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ConcertoServiceImpl implements ConcertoService {
    private static final Logger log = LoggerFactory.getLogger(ConcertoServiceImpl.class);

    private final WebClient webClient;

    public ConcertoServiceImpl(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://api.predicthq.com/v1/events/").build();
    }

    @Override
    public List<ConcertoDTO> buscarConcertosDePortugal() {
        try {
            String API_TOKEN = "fGA-mayju-EQMD0pQ53o3T7bL22ySaW-spJtLxcr";
            PredictHQResponse body = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("country", "PT")
                            .queryParam("category", "concerts")
                            .queryParam("limit", 10)
                            // Ajustado para o parâmetro que aparece no seu link de 'next'
                            .queryParam("start_around.origin", "2026-01-01")
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + API_TOKEN.trim())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class).flatMap(error -> {
                                log.error("Erro da API PredictHQ: {}", error);
                                return Mono.error(new RuntimeException("Falha na API: " + error));
                            })
                    )
                    .bodyToMono(PredictHQResponse.class)
                    .block();

            if (body == null || body.getResults() == null) return List.of();

            return body.getResults().stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Erro ao buscar concertos: ", e);
            throw e;
        }
    }

    private ConcertoDTO convertToDTO(PredictHQResponse.Event event) {
        String start = event.getStart();

        // Lógica inteligente para buscar o nome do Local (Venue) e não o nome do Artista
        String local = "Local a confirmar";
        if (event.getEntities() != null) {
            local = event.getEntities().stream()
                    .filter(entity -> "venue".equalsIgnoreCase(entity.getType()))
                    .map(PredictHQResponse.Entity::getName)
                    .findFirst()
                    .orElse(event.getEntities().isEmpty() ? "Local a confirmar" : event.getEntities().getFirst().getName());
        }

        return new ConcertoDTO(
                event.getId(),
                event.getTitle(),
                (start != null && start.length() >= 10) ? start.substring(0, 10) : "",
                (start != null && start.length() >= 16) ? start.substring(11, 16) : "",
                local,
                "Portugal", // A API não envia uma string de localização simples no root, mantemos fixo ou usamos as coordenadas
                event.getCategory()
        );
    }
}