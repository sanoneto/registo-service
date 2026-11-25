package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.response.ComplexTrainingSaveDTO;
import com.aneto.registo_horas_service.dto.response.PlanoPagamentoDTO;
import com.aneto.registo_horas_service.dto.response.RegistoTreinoDTO;
import com.aneto.registo_horas_service.mapper.PlanoPagamentoMapper;
import com.aneto.registo_horas_service.mapper.RegistoTreinoMapper;
import com.aneto.registo_horas_service.models.Training.PlanoPagamento;
import com.aneto.registo_horas_service.models.Training.RegistoTreino;
import com.aneto.registo_horas_service.queue.EmailProducer;
import com.aneto.registo_horas_service.repository.PlanoPagamentoRepository;
import com.aneto.registo_horas_service.repository.RegistroTreinoRepository;
import com.aneto.registo_horas_service.service.ComprovativoAssinaturaPdfService;
import com.aneto.registo_horas_service.service.RegistroTreinoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistroTreinoServiceImpl implements RegistroTreinoService {
    private static final Set<String> ROLES_PERMITIDAS_ENVIO_COMPROVATIVO = Set.of(
            "ROLE_ADMIN",
            "ROLE_PERSONAL_TRAINER",
            "ROLE_GINASIO"
    );
    private final RegistroTreinoRepository treinoRepo;
    private final PlanoPagamentoRepository planoRepo;

    private final PlanoPagamentoMapper planoMapper;
    private final RegistoTreinoMapper treinoMapper;

    private final ComprovativoAssinaturaPdfService comprovativoPdfService;
    private final EmailProducer emailProducer;

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

        PlanoPagamento planoAfetado = null;
        String emailInformado = null;

        // 1. Gravar novos planos de pagamento
        if (dto.getPayments() != null && !dto.getPayments().isEmpty()) {
            List<PlanoPagamento> novasEntidadesPlano = dto.getPayments().stream()
                    .map(planoMapper::toPlanoPagamento)
                    .collect(Collectors.toList());

            List<PlanoPagamento> planosSalvos = planoRepo.saveAllAndFlush(novasEntidadesPlano);
            planoAfetado = planosSalvos.get(planosSalvos.size() - 1);
            emailInformado = dto.getPayments().get(0).getEmailSocio();
        }

        // 2. Processar registos de treino
        if (dto.getTrainings() != null && !dto.getTrainings().isEmpty()) {
            List<RegistoTreino> treinosParaSalvar = new ArrayList<>();
            int aulasAcumuladasNoLote = 0;

            for (var treinoDTO : dto.getTrainings()) {
                if (treinoDTO.getNoSocio() == null || treinoDTO.getNoSocio().isBlank()) {
                    throw new IllegalArgumentException("O número do sócio é obrigatório.");
                }

                if (emailInformado == null && treinoDTO.getEmailSocio() != null && !treinoDTO.getEmailSocio().isBlank()) {
                    emailInformado = treinoDTO.getEmailSocio();
                }

                RegistoTreino t = treinoMapper.toRegistoTreino(treinoDTO);

                PlanoPagamento planoAtual = planoRepo.findFirstByNoSocioOrderByIdDesc(t.getNoSocio())
                        .orElseThrow(() -> new RuntimeException("Não existe plano ativo para o sócio " + t.getNoSocio()));

                planoAfetado = planoAtual;
                t.setPlanoPagamento(planoAtual);

                t.setPackName(planoAtual.getPackName());
                t.setValor(planoAtual.getValor());
                t.setPackValor(planoAtual.getValor());
                t.setAulasPack(planoAtual.getAulasPack());

                if ("PACK".equalsIgnoreCase(planoAtual.getTipoPack())) {
                    long treinosRealizados = treinoRepo.countByPlanoPagamentoId(planoAtual.getId());
                    int aulasFeitas = t.getAulasFeitas() > 0 ? t.getAulasFeitas() : 1;
                    int totalAposEsteTreino = (int) treinosRealizados + aulasAcumuladasNoLote + aulasFeitas;

                    if (totalAposEsteTreino > planoAtual.getAulasPack()) {
                        throw new RuntimeException("Limite do Pack atingido para o sócio: " + t.getNoSocio());
                    }

                    aulasAcumuladasNoLote += aulasFeitas;
                    t.setSaldo(planoAtual.getAulasPack() - totalAposEsteTreino);
                } else {
                    t.setSaldo(999);
                }

                treinosParaSalvar.add(t);
            }

            treinoRepo.saveAllAndFlush(treinosParaSalvar);
        }

        // 3. Gerar comprovativo PDF assinado e enviar via EmailProducer
        if (planoAfetado != null) {
            enviarComprovativoPorEmail(planoAfetado, emailInformado);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void reenviarComprovativoAssinaturas(Long planoPagamentoId) {
        if (!podeEnviarComprovativoAssinaturas()) {
            throw new AccessDeniedException("Apenas Administradores, Personal Trainers ou Ginásios podem enviar comprovativos de assinaturas por e-mail.");
        }

        if (planoPagamentoId == null) {
            throw new IllegalArgumentException("O ID do plano de pagamento é obrigatório.");
        }

        PlanoPagamento plano = planoRepo.findById(planoPagamentoId)
                .orElseThrow(() -> new IllegalArgumentException("Plano de pagamento não encontrado com ID: " + planoPagamentoId));

        enviarComprovativoPorEmail(plano, plano.getEmailSocio());
    }

    private boolean podeEnviarComprovativoAssinaturas() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(String::toUpperCase)
                .anyMatch(ROLES_PERMITIDAS_ENVIO_COMPROVATIVO::contains);
    }

    private void enviarComprovativoPorEmail(PlanoPagamento plano, String emailInformado) {
        try {
            String emailDestino = resolverEmailSocio(plano, emailInformado);
            if (emailDestino == null || emailDestino.isBlank()) {
                log.warn("Nenhum e-mail encontrado para o sócio {} ({}). Comprovativo PDF não enviado.",
                        plano.getNomeSocio(), plano.getNoSocio());
                return;
            }

            List<RegistoTreinoDTO> treinosDoPlano = treinoRepo.findByPlanoPagamentoIdOrderByIdAsc(plano.getId())
                    .stream()
                    .map(treinoMapper::toRegistoTreinoDTO)
                    .collect(Collectors.toList());

            PlanoPagamentoDTO planoDTO = planoMapper.toPlanoPagamentoDTO(plano);
            planoDTO.setRegistosTreino(treinosDoPlano);

            byte[] pdfBytes = comprovativoPdfService.gerarComprovativoPdf(planoDTO, treinosDoPlano);

            String subject = "ProAct — Comprovativo Assinado (" + plano.getPackName() + ")";
            String body = "Olá " + plano.getNomeSocio() + ",\n\n" +
                    "Segue em anexo o comprovativo digital atualizado do seu pack \"" + plano.getPackName() + "\" com os registos assinados.\n\n" +
                    "Com os melhores cumprimentos,\nEquipa ProAct";
            String fileName = "Comprovativo_ProAct_" + plano.getNoSocio() + ".pdf";

            emailProducer.publishEmailWithAttachment(
                    emailDestino.trim(),
                    subject,
                    body,
                    fileName,
                    pdfBytes
            );
        } catch (Exception e) {
            log.error("Erro ao gerar/publicar comprovativo PDF por e-mail para o sócio {}: {}",
                    plano.getNoSocio(), e.getMessage(), e);
        }
    }

    private String resolverEmailSocio(PlanoPagamento plano, String emailInformado) {
        if (emailInformado != null && !emailInformado.isBlank()) {
            return emailInformado;
        }
        if (plano.getEmailSocio() != null && !plano.getEmailSocio().isBlank()) {
            return plano.getEmailSocio();
        }
        return treinoRepo.findEmailByNoSocioInAuth(plano.getNoSocio()).orElse(null);
    }

    @Transactional(readOnly = true)
    public Page<RegistoTreino> listarTodosPaginado(String termo, Pageable paginacao) {
        if (termo == null || termo.trim().isEmpty()) {
            return treinoRepo.findAll(paginacao);
        }
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