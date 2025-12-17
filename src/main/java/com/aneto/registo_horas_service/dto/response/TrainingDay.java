package com.aneto.registo_horas_service.dto.response;

import java.util.List;

public record TrainingDay(String day,
                          List<TrainingExercise> exercises) {
}
