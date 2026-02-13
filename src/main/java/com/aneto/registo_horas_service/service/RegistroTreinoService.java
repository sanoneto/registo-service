package com.aneto.registo_horas_service.service;

import com.aneto.registo_horas_service.dto.response.ComplexTrainingSaveDTO;
import com.aneto.registo_horas_service.models.Training.PlanoPagamento;
import com.aneto.registo_horas_service.models.Training.RegistoTreino;
import com.aneto.registo_horas_service.repository.PlanoPagamentoRepository;
import com.aneto.registo_horas_service.repository.RegistroTreinoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RegistroTreinoService {

    private final RegistroTreinoRepository treinoRepo;
    private final PlanoPagamentoRepository planoRepo;

    public List<RegistoTreino> salvarLista(List<RegistoTreino> registros) {
        if (registros == null || registros.isEmpty()) {
            throw new IllegalArgumentException("A lista de registros não pode estar vazia.");
        }
        return treinoRepo.saveAll(registros);
    }

    @Transactional
    public void salvarTudoComCalculo(ComplexTrainingSaveDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Dados de envio não podem ser nulos.");
        }

        if (dto.getPayments() != null && !dto.getPayments().isEmpty()) {
            planoRepo.saveAll(dto.getPayments());
        }

        if (dto.getTrainings() != null && !dto.getTrainings().isEmpty()) {
            for (RegistoTreino t : dto.getTrainings()) {
                if (t.getNoSocio() == null || t.getNoSocio().isBlank()) {
                    throw new IllegalArgumentException("O número do sócio é obrigatório.");
                }

                PlanoPagamento planoMaisRecente = planoRepo.findAll().stream()
                        .filter(p -> p.getNoSocio().equals(t.getNoSocio()))
                        .max((p1, p2) -> p1.getDataPack().compareTo(p2.getDataPack()))
                        .orElse(null);

                if (planoMaisRecente != null) {
                    t.setPlanoPagamento(planoMaisRecente);
                }

                Integer totalComprado = planoRepo.sumAulasByNoSocio(t.getNoSocio());
                Integer totalGasto = treinoRepo.sumAulasFeitasByNoSocio(t.getNoSocio());

                int comprado = (totalComprado != null) ? totalComprado : 0;
                int gasto = (totalGasto != null) ? totalGasto : 0;

                t.setSaldo(comprado - (gasto + t.getAulasFeitas()));
                treinoRepo.save(t);
            }
        }
    }

    public Page<RegistoTreino> listarTodosPaginado(String termo, Pageable paginacao) {
        if (termo == null || termo.isEmpty()) {
            return treinoRepo.findAll(paginacao);
        }
        // ✅ CORREÇÃO: O nome do método deve bater com o que definires no Repository
        // Se pesquisares no plano: PlanoPagamentoNomeSocio...
        // Se decidires adicionar o campo à entidade: NomeSocio...
        return treinoRepo.findByNoSocioContainingIgnoreCaseOrPlanoPagamentoNomeSocioContainingIgnoreCase(termo, termo, paginacao);
    }

    @Transactional
    public boolean eliminarRegisto(Long id) {
        if (treinoRepo.existsById(id)) {
            treinoRepo.deleteById(id);
            return true;
        }
        return false;
    }
}