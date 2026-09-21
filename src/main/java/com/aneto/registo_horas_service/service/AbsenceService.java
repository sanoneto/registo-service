package com.aneto.registo_horas_service.service;

import com.aneto.registo_horas_service.dto.request.AbsenceRequest;
import com.aneto.registo_horas_service.models.Absence;
import com.aneto.registo_horas_service.models.Enum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface AbsenceService {
    Absence registerAbsence(AbsenceRequest absenceRequest);

    Page<Absence> getAbsencesPage(String userName, Enum.AbsenceStatus status, Pageable pageable);

    void deleteByPublicId(String publicId);

    Optional<Absence> updateAbsenceStatus(String publicId, Enum.AbsenceStatus newStatus);

}
