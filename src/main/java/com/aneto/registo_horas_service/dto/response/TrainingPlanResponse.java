package com.aneto.registo_horas_service.dto.response;

import java.util.List;

public record TrainingPlanResponse(String summary,
                                   List<TrainingDay> plan) {
}
