package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.dto.response.ComplexTrainingSaveDTO;
import com.aneto.registo_horas_service.models.Training.RegistoTreino;
import com.aneto.registo_horas_service.service.RegistroTreinoService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class RegistroTreinoController {

    private final RegistroTreinoService service;

    /**
     * O try-catch foi removido. Se ocorrer um erro, o GlobalExceptionHandler
     * captura e retorna o ErrorResponse formatado.
     */
    @PostMapping("/save-complex")
    public ResponseEntity<String> saveComplex(@RequestBody ComplexTrainingSaveDTO dto) {
        service.salvarTudoComCalculo(dto);
        return ResponseEntity.ok("Dados guardados com sucesso e saldo atualizado.");
    }

    @PostMapping("/salvar-lista")
    public ResponseEntity<Page<RegistoTreino>> salvarLista(
            @RequestBody List<RegistoTreino> registros,
            @PageableDefault(sort = "id", direction = Sort.Direction.ASC, size = 10) Pageable paginacao) {

        List<RegistoTreino> salvos = service.salvarLista(registros);

        int start = (int) paginacao.getOffset();
        int end = Math.min((start + paginacao.getPageSize()), salvos.size());

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
    public ResponseEntity<Page<RegistoTreino>> listarTodos(
            @RequestParam(required = false, defaultValue = "") String termo,
            @PageableDefault(sort = "data", direction = Sort.Direction.DESC, size = 1000) Pageable paginacao) {

        Page<RegistoTreino> pagina = service.listarTodosPaginado(termo, paginacao);
        return ResponseEntity.ok(pagina);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarRegisto(@PathVariable Long id) {
        if (service.eliminarRegisto(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}