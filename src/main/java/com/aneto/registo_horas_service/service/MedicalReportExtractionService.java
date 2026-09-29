package com.aneto.registo_horas_service.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface MedicalReportExtractionService {
    String extractText(MultipartFile file) throws IOException;
}
