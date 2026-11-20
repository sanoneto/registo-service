package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.service.MedicalReportExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class MedicalReportExtractionServiceImpl implements MedicalReportExtractionService {

    private static final int LIMITE_CARACTERES = 6000;
    private static final int MAX_PAGINAS = 5;

    // constantes novas
    private static final Pattern MEDICO = Pattern.compile("(?i)^o\\(a\\)\\s+m[ée]dico\\(a\\)\\s*:?(.*)$");

    private static final List<Pattern> LINHAS_A_IGNORAR = List.of(
            Pattern.compile("(?i)p[áa]gina\\s+\\d+\\s+de\\s+\\d+"),
            Pattern.compile("(?i)\\b(tel|telefone|telem[óo]vel|fax)\\s*[:.]"),
            Pattern.compile("(?i)e-?mail\\s*:"),
            Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+"),
            Pattern.compile("(?i)^servi[çc]o de .*hospital")
    );

    private final ChatModel chatModel;

    @Override
    public String extractText(MultipartFile file) throws IOException {
        String nome = file.getOriginalFilename();
        if (nome == null || !nome.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Apenas ficheiros PDF são suportados.");
        }

        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String texto = stripper.getText(doc);

            if (texto == null || texto.isBlank()) {
                log.info("PDF sem texto selecionável. A transcrever via IA multimodal...");
                texto = transcreverComIa(doc);
            }

            if (texto == null || texto.isBlank()) {
                throw new IllegalStateException("Não foi possível extrair texto do documento.");
            }
            texto = removerDadosIrrelevantes(texto);   // <-- novo
            log.info("Texto extraído. Tamanho: {} caracteres", texto.length());
            return truncar(texto);
        }
    }

    private String transcreverComIa(PDDocument doc) throws IOException {
        PDFRenderer renderer = new PDFRenderer(doc);
        List<Media> medias = new ArrayList<>();

        int paginas = Math.min(doc.getNumberOfPages(), MAX_PAGINAS);
        for (int i = 0; i < paginas; i++) {
            BufferedImage img = renderer.renderImageWithDPI(i, 150);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            medias.add(new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(baos.toByteArray())));
        }

        UserMessage msg = new UserMessage(
                "Transcreve fielmente o texto clínico deste documento médico, página a página, em português. "
                        + "IGNORA completamente e NÃO transcrevas: cabeçalhos e rodapés, o nome do médico "
                        + "(incluindo o rótulo 'O(a) Médico(a)'), o nome do serviço/hospital, "
                        + "números de página (ex.: 'Página 1 de 1'), telefones e e-mails. "
                        + "Não resumes, não interpretes, não acrescentes nada.", medias);

        ChatResponse resp = chatModel.call(new Prompt(msg));

        return resp.getResult().getOutput().getContent();
    }

    private String truncar(String texto) {
        String limpo = texto.strip();
        return limpo.length() > LIMITE_CARACTERES
                ? limpo.substring(0, LIMITE_CARACTERES) + "\n[... conteúdo truncado ...]"
                : limpo;
    }

    private String removerDadosIrrelevantes(String texto) {
        StringBuilder sb = new StringBuilder();
        boolean saltarProximaLinhaComTexto = false;

        for (String linha : texto.split("\\R")) {
            String l = linha.strip();

            // O nome do médico costuma vir na linha seguinte ao rótulo "O(a) Médico(a)"
            if (saltarProximaLinhaComTexto) {
                if (l.isEmpty()) continue;
                saltarProximaLinhaComTexto = false;
                continue;
            }

            var m = MEDICO.matcher(l);
            if (m.matches()) {
                // Se o nome estiver na mesma linha, já foi descartado; senão, salta a próxima
                saltarProximaLinhaComTexto = m.group(1).isBlank();
                continue;
            }

            if (LINHAS_A_IGNORAR.stream().anyMatch(p -> p.matcher(l).find())) continue;

            sb.append(linha).append('\n');
        }
        return sb.toString();
    }
}