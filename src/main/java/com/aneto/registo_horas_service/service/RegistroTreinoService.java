package com.aneto.registo_horas_service.service;

import com.aneto.registo_horas_service.dto.response.ComplexTrainingSaveDTO;
import com.aneto.registo_horas_service.models.Training.RegistoTreino;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface RegistroTreinoService {
    List<RegistoTreino> salvarLista(List<RegistoTreino> registros);
    void salvarTudoComCalculo(ComplexTrainingSaveDTO dto);
    void reenviarComprovativoAssinaturas(Long planoPagamentoId);
    Page<RegistoTreino> listarTodosPaginado(String termo, Pageable paginacao);
    boolean eliminarRegisto(Long id);
}
