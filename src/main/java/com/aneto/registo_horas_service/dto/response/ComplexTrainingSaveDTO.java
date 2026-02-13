package com.aneto.registo_horas_service.dto.response;

import com.aneto.registo_horas_service.models.Training.PlanoPagamento;
import com.aneto.registo_horas_service.models.Training.RegistoTreino;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
@Getter
@Setter
public class ComplexTrainingSaveDTO {
    private List<PlanoPagamento> payments;
    private List<RegistoTreino> trainings;
    // Getters e Setters
}