package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.models.Training.RegistoTreino;
import com.aneto.registo_horas_service.repository.RegistroTreinoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/registros-treino")
public class RegistroTreinoController {

    @Autowired
    private RegistroTreinoRepository repository;

    @PostMapping("/salvar-lista")
    public ResponseEntity<String> salvarLista(@RequestBody List<RegistoTreino> registros) {
        repository.saveAll(registros);
        return ResponseEntity.ok("Registros salvos com sucesso!");
    }
    // Endpoint que o React está a chamar e a dar erro:
    @GetMapping("/todos")
    public List<RegistoTreino> listarTodos() {
        // Retorna todos os registos ordenados pela data mais recente
        return repository.findAll(Sort.by(Sort.Direction.DESC, "data"));
    }
}