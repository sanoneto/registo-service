package com.aneto.registo_horas_service.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class ComplexTrainingSaveDTO {
    // Aqui DEVEM ser DTOs, pois vêm do JSON do Front-end
    private List<PlanoPagamentoDTO> payments;
    private List<RegistoTreinoDTO> trainings;
}