package com.aneto.registo_horas_service.dto.response;

public record TrainingExercise(
        Integer order,
        String name,
        String muscleGroup,
        String equipment,
        String intensity,
        String sets,
        String reps,
        String rest,
        String tempo,
        String details,
        String notas,
        String weight,
        String cargaAtual,
        String videoUrl,
        String date,
        String movementPlane
) {

    public Object videoUrl(String linkByExerciseName) {
        return videoUrl;
    }
}
