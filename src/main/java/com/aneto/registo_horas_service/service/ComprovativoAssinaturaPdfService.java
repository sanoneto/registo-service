package com.aneto.registo_horas_service.service;

import com.aneto.registo_horas_service.dto.response.PlanoPagamentoDTO;
import com.aneto.registo_horas_service.dto.response.RegistoTreinoDTO;

import java.util.List;

public interface ComprovativoAssinaturaPdfService {
    byte[] gerarComprovativoPdf(PlanoPagamentoDTO plano, List<RegistoTreinoDTO> treinos);
}