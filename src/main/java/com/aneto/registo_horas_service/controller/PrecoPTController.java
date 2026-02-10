package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.models.Training.PrecoPT;
import com.aneto.registo_horas_service.service.IPrecoPTService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;



@RestController
@RequestMapping("/api/precos-pt")
public class PrecoPTController {

    // O Spring injeta automaticamente a classe que implementa IPrecoPTService
    @Autowired
    private IPrecoPTService service;

    @GetMapping
    public List<PrecoPT> listar() {
        return service.listarTodos();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PrecoPT> buscar(@PathVariable Long id) {
        return service.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<PrecoPT> criar(@RequestBody PrecoPT precoPT) {
        PrecoPT novoPreco = service.salvar(precoPT);
        return new ResponseEntity<>(novoPreco, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PrecoPT> atualizar(@PathVariable Long id, @RequestBody PrecoPT precoPT) {
        PrecoPT atualizado = service.atualizar(id, precoPT);
        return atualizado != null ? ResponseEntity.ok(atualizado) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        service.deletar(id);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/lista")
    public ResponseEntity<List<PrecoPT>> criarLista(@RequestBody List<PrecoPT> listaPrecos) {
        List<PrecoPT> novosPrecos = new ArrayList<>();
        for (PrecoPT preco : listaPrecos) {
            novosPrecos.add(service.salvar(preco));
        }
        return new ResponseEntity<>(novosPrecos, HttpStatus.CREATED);
    }
}