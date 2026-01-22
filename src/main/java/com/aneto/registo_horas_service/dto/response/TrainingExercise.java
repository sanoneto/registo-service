package com.aneto.registo_horas_service.dto.response;

public record TrainingExercise(
        Integer order,
        String name,
        String muscleGroup,
        String sets,
        String reps,
        String rest,
        String details,
        String notas,
        String weight,
        String cargaAtual,
        String date
) {

}
