package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.models.Training.PrecoPT;
import com.aneto.registo_horas_service.repository.PrecoPTRepository;
import com.aneto.registo_horas_service.service.IPrecoPTService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PrecoPTServiceImpl implements IPrecoPTService {

    @Autowired
    private PrecoPTRepository repository;

    @Override
    public List<PrecoPT> listarTodos() {
        return repository.findAll();
    }

    @Override
    public Optional<PrecoPT> buscarPorId(Long id) {
        return repository.findById(id);
    }

    @Override
    public PrecoPT salvar(PrecoPT precoPT) {
        return repository.save(precoPT);
    }

    @Override
    public PrecoPT atualizar(Long id, PrecoPT precoPT) {
        if (repository.existsById(id)) {
            precoPT.setId(id);
            return repository.save(precoPT);
        }
        return null;
    }

    @Override
    public void deletar(Long id) {
        repository.deleteById(id);
    }
}