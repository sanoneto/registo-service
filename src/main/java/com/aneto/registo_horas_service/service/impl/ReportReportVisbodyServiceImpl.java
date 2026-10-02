package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.service.MedicalReportExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

@Service
@Slf4j
@RequiredArgsConstructor

public class ReportReportVisbodyServiceImpl implements MedicalReportExtractionService {

    private final ChatModel chatModel;

    @Override
    public String extractText(MultipartFile file) throws IOException {
        log.info("A processar ficheiro para análise multimodal: {}", file.getOriginalFilename());

        if (file.getContentType() != null && file.getContentType().equals("application/pdf")) {
            try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
                PDFRenderer pdfRenderer = new PDFRenderer(doc);
                List<Media> mediaList = new ArrayList<>();

                // Converte cada página do PDF numa imagem PNG de alta resolução (300 DPI)
                for (int page = 0; page < doc.getNumberOfPages(); page++) {
                    BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(page, 300);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(bufferedImage, "png", baos);
                    byte[] imageBytes = baos.toByteArray();

                    mediaList.add(new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(imageBytes)));
                }

                // Prompt estruturado para extrair dados clínicos, bioimpedância e postura
                UserMessage userMessage = getUserMessage(mediaList);
                var response = chatModel.call(new Prompt(userMessage));

                return response.getResult().getOutput().getContent();
            } catch (Exception e) {
                log.error("Erro ao processar o relatório de consulta no Spring AI: {}", e.getMessage(), e);
                throw new IOException("Falha ao analisar o relatório com a IA.", e);
            }
        }

        throw new IllegalArgumentException("Tipo de ficheiro não suportado.");
    }

    @NotNull
    private static UserMessage getUserMessage(List<Media> mediaList) {
        String promptTexto = """
                Analisa este relatório de avaliação física/médica (Visbody) e extrai os seguintes dados num formato JSON limpo e estruturado:
                1. Dados do Utente: Idade, Sexo, Altura, Peso.
                2. Composição Corporal: % Gordura (PGC), Massa Magra, Massa Muscular Esquelética (MME), Gordura Visceral, Taxa Metabólica Basal.
                3. Avaliação Postural e Limitações: Identifica desvios significativos (ex: cabeça projetada, anteversão pélvica, assimetria de ombros).
                4. Resumo de Recomendações: Limitações a ter em conta no plano de treino/exercício.
                """;

        return new UserMessage(promptTexto, mediaList);
    }
}