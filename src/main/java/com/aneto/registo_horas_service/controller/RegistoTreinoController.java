package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.dto.response.ComplexTrainingSaveDTO;
import com.aneto.registo_horas_service.dto.response.RegistoTreinoDTO;
import com.aneto.registo_horas_service.mapper.RegistoTreinoMapper;
import com.aneto.registo_horas_service.models.Training.RegistoTreino;
import com.aneto.registo_horas_service.service.impl.RegistroTreinoServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/registros-treino")
@RequiredArgsConstructor
@Slf4j
public class RegistoTreinoController {

    private final RegistroTreinoServiceImpl service;
    private final RegistoTreinoMapper mapper;

    @PostMapping("/save-complex")
    public ResponseEntity<String> saveComplex(@RequestBody ComplexTrainingSaveDTO dto) {
        service.salvarTudoComCalculo(dto);
        return ResponseEntity.ok("Dados guardados com sucesso e saldo atualizado.");
    }

    @PostMapping("/plano/{planoId}/enviar-comprovativo")
    @PreAuthorize("hasAnyRole('ADMIN', 'PERSONAL_TRAINER', 'GINASIO')")
    public ResponseEntity<String> enviarComprovativoAssinaturas(@PathVariable("planoId") Long planoId) {
        log.info("Pedido manual de envio de comprovativo PDF para o plano ID: {}", planoId);
        service.reenviarComprovativoAssinaturas(planoId);
        return ResponseEntity.ok("Comprovativo de assinaturas enviado por e-mail com sucesso.");
    }

    @PostMapping("/salvar-lista")
    public ResponseEntity<Page<RegistoTreinoDTO>> salvarLista(
            @RequestBody List<RegistoTreino> registros,
            @PageableDefault(sort = "id", direction = Sort.Direction.ASC, size = 10) Pageable paginacao) {

        List<RegistoTreino> salvos = service.salvarLista(registros);

        List<RegistoTreinoDTO> dtos = salvos.stream()
                .map(mapper::toRegistoTreinoDTO)
                .collect(Collectors.toList());

        int start = (int) paginacao.getOffset();
        int end = Math.min((start + paginacao.getPageSize()), dtos.size());

        if (start > dtos.size()) {
            return ResponseEntity.ok(new PageImpl<>(List.of(), paginacao, dtos.size()));
        }

        Page<RegistoTreinoDTO> pagina = new PageImpl<>(
                dtos.subList(start, end),
                paginacao,
                dtos.size()
        );

        return ResponseEntity.ok(pagina);
    }

    @GetMapping("/todos")
    public ResponseEntity<Page<RegistoTreinoDTO>> listarTodos(
            @RequestParam(required = false, defaultValue = "") String termo,
            @PageableDefault(sort = "data", direction = Sort.Direction.DESC, size = 1000) Pageable paginacao) {

        Page<RegistoTreinoDTO> pagina = service.listarTodosPaginado(termo, paginacao)
                .map(mapper::toRegistoTreinoDTO);
        log.info("Registos de treino listados: {}", pagina.getContent());
        return ResponseEntity.ok(pagina);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarRegisto(@PathVariable("id") Long id) {
        if (service.eliminarRegisto(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}