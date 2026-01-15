package com.aneto.registo_horas_service.dto.response;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
@Data
@Getter
@Setter
public class PredictHQResponse {
    private List<Event> results;

    @Setter
    @Getter
    public static class Event {
        // Setters (importantes para o RestTemplate preencher os dados)
        // Getters indispensáveis para o Controller
        private String id;
        private String title;
        private String start;
        private String category;
        private List<String> location;
        private List<Entity> entities;

    }
    @Data
    @Getter
    @Setter
    public static class Entity {
        private String name;
        private String type; // <--- ADICIONE ESTE CAMPO
    }
}