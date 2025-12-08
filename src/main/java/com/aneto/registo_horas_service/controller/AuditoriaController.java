package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.models.RegistoHistorico;
import com.aneto.registo_horas_service.repository.RegistoHistoricoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

// Importe sua classe de Entidade e Repositório
// import com.seuprojeto.model.RegistoHistorico;
// import com.seuprojeto.repository.RegistoHistoricoRepository;

@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    // Assumindo que este repositório existe e estende JpaRepository
    @Autowired
    private RegistoHistoricoRepository historicoRepository;

    /**
     * Endpoint: GET /api/auditoria/registo/{publicId}
     * Objetivo: Buscar o histórico completo de alterações (CREATE, UPDATE, DELETE) para um único registo de horas,
     * identificado pelo seu publicId.
     *
     * @param publicId O identificador público do Registo de Horas.
     * @return Uma lista de objetos RegistoHistorico, ordenados pela data de alteração.
     */
    @GetMapping("/registo/{publicId}")
    public ResponseEntity<List<RegistoHistorico>> getHistoricoByRegistoId(@PathVariable String publicId) {

        // 1. Ação Principal: Buscar todas as entradas de histórico.
        // O método no repositório usa a convenção de nomes do Spring Data JPA:
        // 'findBy' + 'RegistoPublicId' (campo na entidade) + 'OrderBy' + 'DataAlteracao' (campo) + 'Desc' (direção)
        List<RegistoHistorico> historico = historicoRepository.findByRegistoPublicIdOrderByDataAlteracaoDesc(publicId);

        // 2. Resposta
        if (historico.isEmpty()) {
            // Retorna 404 Not Found se não houver histórico para o ID (embora o CREATE deva gerar um)
            return ResponseEntity.notFound().build();
        }

        // Retorna a lista de entradas de histórico com status 200 OK
        // O Spring serializará automaticamente a lista para JSON.
        return ResponseEntity.ok(historico);
    }

    /**
     * Endpoint: GET /api/auditoria/por-utilizador?username={utilizador}
     * Objetivo: Listar todos os registos de auditoria (CREATE, UPDATE, DELETE) feitos por um utilizador específico.
     *
     * @param username O nome de utilizador (userName) que realizou a alteração.
     * @return Uma lista de objetos RegistoHistorico.
     */
    @GetMapping("/por-utilizador")
    public ResponseEntity<List<RegistoHistorico>> getHistoricoByUtilizador(@RequestParam("username") String username) {

        // Método no repositório: findByUtilizadorAlteracaoOrderByDataAlteracaoDesc
        List<RegistoHistorico> historico = historicoRepository.findByUtilizadorAlteracaoOrderByDataAlteracaoDesc(username);

        if (historico.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(historico);
    }
}
