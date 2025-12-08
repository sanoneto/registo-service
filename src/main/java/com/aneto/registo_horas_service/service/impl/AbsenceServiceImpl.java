package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.request.AbsenceRequest;
import com.aneto.registo_horas_service.models.Absence;
import com.aneto.registo_horas_service.models.AbsenceStatus;
import com.aneto.registo_horas_service.repository.AbsenceRepository;
import com.aneto.registo_horas_service.service.AbsenceService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AbsenceServiceImpl implements AbsenceService {

    private final AbsenceRepository absenceRepository;
    @Override
    public Absence registerAbsence(AbsenceRequest absenceRequest) {

        // 1. Validação de Regra de Negócio (Fim não antes do Início)
        if (absenceRequest.endDate().isBefore(absenceRequest.startDate())) {
            throw new IllegalArgumentException("A data de fim não pode ser anterior à data de início.");
        }

        // 2. Mapeamento DTO para Entidade
        Absence absence = Absence.builder()
                .userName(absenceRequest.userName())
                .endDate(absenceRequest.endDate())
                .startDate(absenceRequest.startDate())
                .reason(absenceRequest.reason())
                .type(absenceRequest.type())
                .build();
        // As anotações @PrePersist na Entidade cuidam do publicId, registrationDate e status (PENDENTE)

        // 3. Salvar no banco de dados
        return absenceRepository.save(absence);
    }

    @Override
    public Page<Absence> getAbsencesPage(String userName, AbsenceStatus status, Pageable pageable) {
        if (userName != null && !userName.isEmpty()) {
            if (status != null) {
                // Filtra por Usuário E Status
                return absenceRepository.findByUserNameAndStatus(userName, status, pageable);
            } else {
                // Filtra apenas por Usuário
                return absenceRepository.findByUserName(userName, pageable);
            }
        } else {
            // ADMIN ou usuário sem userName (vê tudo ou filtra por status)
            if (status != null) {
                // Filtra apenas por Status
                return absenceRepository.findByStatus(status, pageable);
            } else {
                // ADMIN vê todos (sem filtros)
                return absenceRepository.findAll(pageable);
            }
        }
    }

    @Override
    @Transactional
    public void deleteByPublicId(String publicId) {
        // Antes de excluir, é bom verificar se existe (depende da sua regra de negócio)
        absenceRepository.deleteByPublicId(publicId);
    }

    @Transactional
    public Optional<Absence> updateAbsenceStatus(String publicId, AbsenceStatus newStatus) {

        // 1. Encontra a ausência pelo publicId (necessita do método findByPublicId no Repositório)
        Optional<Absence> optionalAbsence = absenceRepository.findByPublicId(publicId);

        if (optionalAbsence.isPresent()) {
            Absence absence = optionalAbsence.get();
            absence.setStatus(newStatus);
            // 3. Salva a alteração (o Spring Data JPA salva automaticamente no final do @Transactional,
            // mas chamar save garante a persistência imediata e retorna o objeto atualizado)
            return Optional.of(absenceRepository.save(absence));
        }
        // Retorna Optional vazio se a ausência não for encontrada
        return Optional.empty();
    }


}
