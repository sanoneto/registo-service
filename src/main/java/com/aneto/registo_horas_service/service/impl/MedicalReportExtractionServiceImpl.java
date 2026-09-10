package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.service.MedicalReportExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@Slf4j
@RequiredArgsConstructor
public class MedicalReportExtractionServiceImpl implements MedicalReportExtractionService {

    private static final int LIMITE_CARACTERES = 6000; // proteção contra ficheiros enormes

    public String extractText(MultipartFile file) throws IOException {
        String contentType = file.getContentType();

        if (contentType != null && contentType.equals("application/pdf")) {
            try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
                PDFTextStripper stripper = new PDFTextStripper();
                String texto = stripper.getText(doc);
                return truncar(texto);
            }
        }

        if (contentType != null && contentType.startsWith("image/")) {
            // Imagens (ex: foto de relatório) exigem OCR ou um modelo multimodal.
            // Se o teu ChatModel suportar imagens (Spring AI Media), passa a imagem
            // diretamente numa UserMessage em vez de extrair texto aqui.
            throw new UnsupportedOperationException(
                    "Extração de texto de imagens ainda não implementada — ver nota sobre multimodal."
            );
        }

        throw new IllegalArgumentException("Tipo de ficheiro não suportado: " + contentType);
    }

    private String truncar(String texto) {
        if (texto == null) return "";
        String limpo = texto.strip();
        return limpo.length() > LIMITE_CARACTERES
                ? limpo.substring(0, LIMITE_CARACTERES) + "\n[... conteúdo truncado ...]"
                : limpo;
    }
}
