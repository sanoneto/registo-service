package com.aneto.registo_horas_service.service;

import com.aneto.registo_horas_service.dto.response.ComplexTrainingSaveDTO;
import com.aneto.registo_horas_service.mapper.PlanoPagamentoMapper;
import com.aneto.registo_horas_service.mapper.RegistoTreinoMapper;
import com.aneto.registo_horas_service.models.Training.PlanoPagamento;
import com.aneto.registo_horas_service.models.Training.RegistoTreino;
import com.aneto.registo_horas_service.repository.PlanoPagamentoRepository;
import com.aneto.registo_horas_service.repository.RegistroTreinoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegistroTreinoService {

    private final RegistroTreinoRepository treinoRepo;
    private final PlanoPagamentoRepository planoRepo;

    private final PlanoPagamentoMapper planoMapper;
    private final RegistoTreinoMapper treinoMapper;

    /**
     * Salva uma lista simples de entidades de treino.
     */
    public List<RegistoTreino> salvarLista(List<RegistoTreino> registros) {
        if (registros == null || registros.isEmpty()) {
            throw new IllegalArgumentException("A lista de registros não pode estar vazia.");
        }
        return treinoRepo.saveAll(registros);
    }

    /**
     * Processa o salvamento complexo: registra planos e treinos,
     * validando o saldo de aulas caso o plano seja do tipo PACK.
     */
    @Transactional
    public void salvarTudoComCalculo(ComplexTrainingSaveDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Dados de envio não podem ser nulos.");
        }

        // 1. Gravar novos planos de pagamento e obter instâncias salvas com ID
        if (dto.getPayments() != null && !dto.getPayments().isEmpty()) {
            List<PlanoPagamento> novasEntidadesPlano = dto.getPayments().stream()
                    .map(planoMapper::toPlanoPagamento)
                    .collect(Collectors.toList());

            // Força o persist e o flush na base de dados para garantir IDs gerados
            planoRepo.saveAllAndFlush(novasEntidadesPlano);
        }

        // 2. Processar registos de treino
        if (dto.getTrainings() != null && !dto.getTrainings().isEmpty()) {
            List<RegistoTreino> treinosParaSalvar = new ArrayList<>();

            for (var treinoDTO : dto.getTrainings()) {
                if (treinoDTO.getNoSocio() == null || treinoDTO.getNoSocio().isBlank()) {
                    throw new IllegalArgumentException("O número do sócio é obrigatório.");
                }

                // Converter DTO para Entidade
                RegistoTreino t = treinoMapper.toRegistoTreino(treinoDTO);

                // Procurar o plano mais recente do sócio
                // Procurar o plano mais recente do sócio
                PlanoPagamento planoAtual = planoRepo.findFirstByNoSocioOrderByIdDesc(t.getNoSocio())
                        .orElseThrow(() -> new RuntimeException("Não existe plano ativo para o sócio " + t.getNoSocio()));

// Associar o plano à entidade
                t.setPlanoPagamento(planoAtual);

// ---> PREENCHER CAMPOS OBRIGATÓRIOS DA BD A PARTIR DO PLANO ATIVO <---
                t.setPackName(planoAtual.getPackName());
                t.setValor(planoAtual.getValor());
                t.setPackValor(planoAtual.getValor());
                t.setAulasPack(planoAtual.getAulasPack());

// Lógica de cálculo do saldo...
                if ("PACK".equalsIgnoreCase(planoAtual.getTipoPack())) {
                    long treinosRealizados = treinoRepo.countByPlanoPagamentoId(planoAtual.getId());

                    if (treinosRealizados + t.getAulasFeitas() > planoAtual.getAulasPack()) {
                        throw new RuntimeException("Limite do Pack atingido para o sócio: " + t.getNoSocio());
                    }

                    t.setSaldo((int) (planoAtual.getAulasPack() - (treinosRealizados + t.getAulasFeitas())));
                } else {
                    t.setSaldo(999);
                }

                treinosParaSalvar.add(t);
            }

            // Persistir todos os treinos processados de uma só vez
            treinoRepo.saveAll(treinosParaSalvar);
        }
    }

    /**
     * Lista todos os treinos de forma paginada, permitindo busca por termo.
     */
    @Transactional(readOnly = true)
    public Page<RegistoTreino> listarTodosPaginado(String termo, Pageable paginacao) {
        if (termo == null || termo.trim().isEmpty()) {
            return treinoRepo.findAll(paginacao);
        }
        return treinoRepo.findByNoSocioContainingIgnoreCaseOrPlanoPagamentoNomeSocioContainingIgnoreCase(termo, termo, paginacao);
    }

    /**
     * Elimina um registo de treino pelo ID.
     */
    @Transactional
    public boolean eliminarRegisto(Long id) {
        if (treinoRepo.existsById(id)) {
            treinoRepo.deleteById(id);
            return true;
        }
        return false;
    }
}