package com.aneto.registo_horas_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseProgressLog {
    private String exerciseName;
    private String muscleGroup;
    private String weight; // Se o TS enviar como 'weight'
    private String cargaAtual;
    private String date;
}