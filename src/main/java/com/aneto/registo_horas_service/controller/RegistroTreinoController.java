package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.models.Training.RegistoTreino;
import com.aneto.registo_horas_service.service.RegistroTreinoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/registros-treino")
public class RegistroTreinoController {

    @Autowired
    private RegistroTreinoService service;

    /**
     * Salva uma lista de registros e retorna a página correspondente.
     */
    @PostMapping("/salvar-lista")
    public ResponseEntity<Page<RegistoTreino>> salvarLista(
            @RequestBody List<RegistoTreino> registros,
            @PageableDefault(sort = "id", direction = Sort.Direction.ASC, size = 10) Pageable paginacao) {

        // 1. Salva os registros e recebe a lista de volta
        List<RegistoTreino> salvos = service.salvarLista(registros);

        // 2. Converte a lista salva em um objeto Page
        int start = (int) paginacao.getOffset();
        int end = Math.min((start + paginacao.getPageSize()), salvos.size());

        // Validação de segurança para garantir que o start não exceda o tamanho da lista
        if (start > salvos.size()) {
            return ResponseEntity.ok(new PageImpl<>(List.of(), paginacao, salvos.size()));
        }

        Page<RegistoTreino> pagina = new PageImpl<>(
                salvos.subList(start, end),
                paginacao,
                salvos.size()
        );

        return ResponseEntity.ok(pagina);
    }

    @GetMapping("/todos")
    public Page<RegistoTreino> listarTodos(
            @RequestParam(required = false, defaultValue = "") String termo,
            @PageableDefault(sort = "data", direction = Sort.Direction.DESC, size = 10) Pageable paginacao) {

        return service.listarTodosPaginado(termo, paginacao);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarRegisto(@PathVariable Long id) {
        if (service.eliminarRegisto(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}