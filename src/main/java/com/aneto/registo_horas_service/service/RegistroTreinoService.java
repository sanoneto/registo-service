package com.aneto.registo_horas_service.service;

import com.aneto.registo_horas_service.models.Training.RegistoTreino;
import com.aneto.registo_horas_service.repository.RegistroTreinoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RegistroTreinoService {

    @Autowired
    private RegistroTreinoRepository repository;

    public Page<RegistoTreino> listarTodosPaginado(String termo, Pageable paginacao) {
        if (termo != null && !termo.isEmpty()) {
            return repository.findByNomeSocioContainingIgnoreCaseOrNoSocioContaining(termo, termo, paginacao);
        }
        return repository.findAll(paginacao);
    }

    public List<RegistoTreino> salvarLista(List<RegistoTreino> registros) {
        repository.saveAll(registros);
        return registros;
    }

    public boolean eliminarRegisto(Long id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return true;
        }
        return false;
    }
}