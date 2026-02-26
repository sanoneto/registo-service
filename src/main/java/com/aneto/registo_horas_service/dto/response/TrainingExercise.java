package com.aneto.registo_horas_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingExercise {
    private Integer order;
    private String name;
    private String muscleGroup;
    private String equipment;
    private String intensity;
    private String sets;
    private String reps;
    private String rest;
    private String tempo;
    private String details;
    private String notas;
    private String weight;
    private String cargaAtual;
    private String videoUrl;
    private String date;
    private String movementPlane;

    // Removido o método 'public Object videoUrl(String linkByExerciseName)'
    // que existia no record, pois ele causava confusão na serialização do Jackson.
    // O valor deve ser atribuído via setter ou builder no serviço.
}