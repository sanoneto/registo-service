package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.service.TargetServiceClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class TargetServiceClientImpl implements TargetServiceClient {

    private static final Logger log = LoggerFactory.getLogger(TargetServiceClientImpl.class);

    // O Spring injeta o WebClient configurado em WebClientConfig
    private final WebClient targetServiceWebClient;

    /**
     * Faz uma chamada HTTP PUT para o microsserviço de autenticação
     * para atualizar a URL da foto de perfil do utilizador, usando @RequestParam.
     */
    @Override
    public void updateProfilePicUrl(String username, String publicUrl) {
        log.info("A iniciar chamada PUT para o micro-serviço de Auth para atualizar a URL para: {}", publicUrl);

        final String API_PATH = "/";
        try {
            targetServiceWebClient.put() // CORREÇÃO 1: Deve ser PUT (conforme a API remota)

                    // CORREÇÃO 2: Constrói a URI com os parâmetros de consulta
                    .uri(uriBuilder -> uriBuilder
                            .path(API_PATH) // O caminho base é "/"
                            .queryParam("username", username) // Adiciona username como ?username=...
                            .queryParam("publicUrl", publicUrl) // Adiciona publicUrl como &publicUrl=...
                            .build()
                    )
                    // CORREÇÃO 3: Remove o .bodyValue() porque a API não espera corpo, só RequestParams

                    .retrieve()
                    // Trata as respostas
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), clientResponse -> {
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Erro ao chamar o Auth Service. Status: {}, Corpo do Erro: {}",
                                            clientResponse.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException("Falha ao atualizar a URL do perfil no Auth Service."));
                                });
                    })
                    // A API retorna um corpo com status 200, então usamos bodyToMono(String.class)
                    .bodyToMono(String.class)
                    .block(); // Bloqueia para obter o resultado (se for síncrono)

            log.info("URL de perfil atualizada com sucesso no Auth Service para o user: {}", username);

        } catch (Exception e) {
            log.error("Exceção durante a comunicação com o Auth Service: {}", e.getMessage());
            throw new RuntimeException("Não foi possível comunicar com o serviço de autenticação.", e);
        }
    }
}