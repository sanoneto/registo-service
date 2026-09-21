package com.aneto.registo_horas_service.service;


import com.aneto.registo_horas_service.models.Training.PrecoPT;

import java.util.List;
import java.util.Optional;

public interface IPrecoPTService {
    List<PrecoPT> listarTodos();
    Optional<PrecoPT> buscarPorId(Long id);
    PrecoPT salvar(PrecoPT precoPT);
    PrecoPT atualizar(Long id, PrecoPT precoPT);
    void deletar(Long id);
}