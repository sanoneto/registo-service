package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.service.MedicalReportExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
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
        log.info("A processar ficheiro para relatório VisBody: {}", file.getOriginalFilename());

        if (file.getContentType() != null && file.getContentType().equals("application/pdf")) {
            try (PDDocument doc = Loader.loadPDF(file.getBytes())) {

                // 1. Tenta extrair texto nativo primeiro (mais rápido e consome menos memória)
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                String textoDireto = stripper.getText(doc);

                if (textoDireto != null && textoDireto.strip().length() > 300) {
                    log.info("VisBody possui texto nativo selecionável ({} caracteres). A processar via LLM textual...", textoDireto.strip().length());
                    return processarTextoDireto(textoDireto);
                }

                // 2. Fallback para análise multimodal (imagens a 150 DPI para otimizar tamanho/memória)
                log.info("VisBody sem texto selecionável/digitalizado. A converter páginas para análise multimodal...");
                return processarMultimodal(doc);

            } catch (Exception e) {
                log.error("Erro ao processar o relatório VisBody no Spring AI: {}", e.getMessage(), e);
                throw new IOException("Falha ao analisar o relatório VisBody com a IA.", e);
            }
        }

        throw new IllegalArgumentException("Tipo de ficheiro não suportado. Envia um documento PDF.");
    }

    private String processarTextoDireto(String textoPdf) {
        String promptTexto = """
                Analisa o texto extraído deste relatório de avaliação física/médica (Visbody) e gera um resumo estruturado em JSON com:
                1. Dados do Utente: Idade, Sexo, Altura, Peso.
                2. Composição Corporal: % Gordura (PGC), Massa Magra, Massa Muscular Esquelética (MME), Gordura Visceral, Taxa Metabólica Basal (BMR).
                3. Avaliação Postural e Limitações: Identifica desvios significativos (ex: cabeça projetada, anteversão pélvica, assimetria de ombros).
                4. Resumo de Recomendações: Limitações e cuidados a ter em conta no treino.

                Texto do Relatório:
                """ + textoPdf;

        UserMessage userMessage = new UserMessage(promptTexto);
        var response = chatModel.call(new Prompt(userMessage));
        return response.getResult().getOutput().getContent();
    }

    private String processarMultimodal(PDDocument doc) throws IOException {
        PDFRenderer pdfRenderer = new PDFRenderer(doc);
        List<Media> mediaList = new ArrayList<>();

        int maxPaginas = Math.min(doc.getNumberOfPages(), 5);
        for (int page = 0; page < maxPaginas; page++) {
            // Renderização otimizada a 150 DPI para equilíbrio entre clareza de OCR e tamanho do payload
            BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(page, 150);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", baos);
            byte[] imageBytes = baos.toByteArray();

            mediaList.add(new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(imageBytes)));
        }

        UserMessage userMessage = getUserMessageMultimodal(mediaList);
        var response = chatModel.call(new Prompt(userMessage));
        return response.getResult().getOutput().getContent();
    }

    @NotNull
    private static UserMessage getUserMessageMultimodal(List<Media> mediaList) {
        String promptTexto = """
                Analisa este relatório de avaliação física/médica (Visbody) e extrai os seguintes dados num formato JSON limpo e estruturado:
                1. Dados do Utente: Idade, Sexo, Altura, Peso.
                2. Composição Corporal: % Gordura (PGC), Massa Magra, Massa Muscular Esquelética (MME), Gordura Visceral, Taxa Metabólica Basal (BMR).
                3. Avaliação Postural e Limitações: Identifica desvios significativos (ex: cabeça projetada, anteversão pélvica, assimetria de ombros).
                4. Resumo de Recomendações: Limitações a ter em conta no plano de treino/exercício.
                """;

        return new UserMessage(promptTexto, mediaList);
    }
}