package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.ClinicalDay;
import com.aneto.registo_horas_service.dto.response.TrainingDay;
import com.aneto.registo_horas_service.dto.response.TrainingExercise;
import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;
import com.aneto.registo_horas_service.service.PdfService;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.util.List;

@Slf4j
@Service
public class PdfServiceImpl implements PdfService {
    @Override
    public byte[] generatePdfBytes(TrainingPlanResponse plan) {
        // Margem inferior de 45pt para acomodar o rodapé paginado ("Página X de Y")
        Document document = new Document(PageSize.A4, 32, 32, 30, 45);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);

            UserProfileRequest profile = plan.getUserProfile();
            String studentName = resolveStudentName(profile);
            String docTitleFooter = "Plano de Treino Clínico"
                    + (!studentName.isBlank() ? " — " + studentName : "");

            writer.setPageEvent(new ClinicalPdfFooterEvent(docTitleFooter));
            document.open();

            // Paleta institucional limpa
            Color navyPrimary = new Color(23, 55, 94);      // Azul-marinho institucional
            Color calloutBg = new Color(240, 245, 250);     // Fundo azul claro para diretrizes
            Color calloutBorder = new Color(23, 55, 94);    // Borda lateral esquerda
            Color tableHeaderBg = new Color(224, 232, 240); // Cabeçalho da tabela
            Color zebraBg = new Color(248, 250, 252);       // Linhas alternadas
            Color gridBorder = new Color(203, 213, 225);    // Linhas divisórias suaves

            Font bannerTagFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, new Color(191, 219, 254));
            Font bannerTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.WHITE);
            Font bannerSubFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(226, 232, 240));

            Font sectionTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11.5f, navyPrimary);
            Font calloutTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, navyPrimary);
            Font calloutTextFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, new Color(30, 41, 59));

            Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, navyPrimary);
            Font exNameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Color.BLACK);
            Font textFont = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(30, 41, 59));
            Font noteFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7.5f, new Color(71, 85, 105));

            // 1. CABEÇALHO SUPERIOR INSTITUCIONAL (Faixa Azul-Marinho)
            PdfPTable headerBanner = new PdfPTable(1);
            headerBanner.setWidthPercentage(100);
            headerBanner.setSpacingAfter(10);

            PdfPCell bannerCell = new PdfPCell();
            bannerCell.setBackgroundColor(navyPrimary);
            bannerCell.setPaddingTop(12);
            bannerCell.setPaddingBottom(12);
            bannerCell.setPaddingLeft(14);
            bannerCell.setPaddingRight(14);
            bannerCell.setBorder(Rectangle.NO_BORDER);

            Paragraph tag = new Paragraph("PRESCRIÇÃO DE EXERCÍCIO CLÍNICO E TREINO PERSONALIZADO", bannerTagFont);
            tag.setSpacingAfter(3);
            bannerCell.addElement(tag);

            String mainHeadline = !studentName.isBlank()
                    ? "Plano de Treino — " + studentName
                    : "Plano de Treino Clínico & Reabilitação";
            Paragraph mainTitle = new Paragraph(mainHeadline, bannerTitleFont);
            mainTitle.setSpacingAfter(4);
            bannerCell.addElement(mainTitle);

            String metaLine = buildMetaLine(profile);
            if (!metaLine.isBlank()) {
                Paragraph metaPara = new Paragraph(metaLine, bannerSubFont);
                bannerCell.addElement(metaPara);
            }

            headerBanner.addCell(bannerCell);
            document.add(headerBanner);

            // 2. CAIXA DE DIRETRIZES BIOMECÂNICAS E CLÍNICAS (Patologia / Prescritor)
            if (profile != null && ((profile.getPathology() != null && !profile.getPathology().isBlank())
                    || (profile.getRecommended() != null && !profile.getRecommended().isBlank()))) {

                PdfPTable calloutTable = new PdfPTable(1);
                calloutTable.setWidthPercentage(100);
                calloutTable.setSpacingAfter(12);

                PdfPCell calloutCell = new PdfPCell();
                calloutCell.setBackgroundColor(calloutBg);
                calloutCell.setBorderColor(gridBorder);
                calloutCell.setBorderWidthTop(0.5f);
                calloutCell.setBorderWidthRight(0.5f);
                calloutCell.setBorderWidthBottom(0.5f);
                calloutCell.setBorderWidthLeft(3.5f);
                calloutCell.setBorderColorLeft(calloutBorder);
                calloutCell.setPadding(9);

                Paragraph cTitle = new Paragraph("Diretrizes Biomecânicas e Clínicas:", calloutTitleFont);
                cTitle.setSpacingAfter(3);
                calloutCell.addElement(cTitle);

                if (profile.getPathology() != null && !profile.getPathology().isBlank()) {
                    Paragraph pPath = new Paragraph("• Quadro Clínico / Condição: " + profile.getPathology(), calloutTextFont);
                    pPath.setSpacingAfter(2);
                    calloutCell.addElement(pPath);
                }
                if (profile.getRecommended() != null && !profile.getRecommended().isBlank()) {
                    Paragraph pRec = new Paragraph("• Especialista / Prescritor: " + profile.getRecommended(), calloutTextFont);
                    calloutCell.addElement(pRec);
                }

                calloutTable.addCell(calloutCell);
                document.add(calloutTable);
            }

            // 3. SESSÕES DE TREINO (ClinicalDay tem diretamente day + List<TrainingExercise> exercises)
            int sessionIndex = 1;
            if (plan.getClinicalPlan() != null && !plan.getClinicalPlan().isEmpty()) {
                for (ClinicalDay day : plan.getClinicalPlan()) {
                    String dayHeading = sessionIndex + ". " + (day.getDay() != null ? day.getDay() : "Sessão " + sessionIndex);
                    Paragraph secTitle = new Paragraph(dayHeading, sectionTitleFont);
                    secTitle.setSpacingBefore(10);
                    secTitle.setSpacingAfter(5);
                    document.add(secTitle);

                    List<TrainingExercise> exercises = day.getExercises();
                    // Fallback caso o clinicalPlan tenha apenas o título do dia e os exercícios estejam em plan.getPlan()
                    if ((exercises == null || exercises.isEmpty())
                            && plan.getPlan() != null && plan.getPlan().size() >= sessionIndex) {
                        exercises = plan.getPlan().get(sessionIndex - 1).getExercises();
                    }

                    if (exercises != null && !exercises.isEmpty()) {
                        document.add(buildClinicalTable(
                                exercises, tableHeaderFont, exNameFont, textFont, noteFont,
                                tableHeaderBg, zebraBg, gridBorder
                        ));
                    }
                    sessionIndex++;
                }
            } else if (plan.getPlan() != null && !plan.getPlan().isEmpty()) {
                for (TrainingDay day : plan.getPlan()) {
                    String dayHeading = sessionIndex + ". " + (day.getDay() != null ? day.getDay() : "Treino " + sessionIndex);
                    Paragraph secTitle = new Paragraph(dayHeading, sectionTitleFont);
                    secTitle.setSpacingBefore(10);
                    secTitle.setSpacingAfter(5);
                    document.add(secTitle);

                    if (day.getExercises() != null && !day.getExercises().isEmpty()) {
                        document.add(buildClinicalTable(
                                day.getExercises(), tableHeaderFont, exNameFont, textFont, noteFont,
                                tableHeaderBg, zebraBg, gridBorder
                        ));
                    }
                    sessionIndex++;
                }
            }

            document.close();
        } catch (Exception ex) {
            log.error("Erro ao gerar PDF clínico", ex);
        }

        return out.toByteArray();
    }

    @Override
    public PdfPTable buildClinicalTable(
            List<TrainingExercise> exercises,
            Font headerFont,
            Font exNameFont,
            Font textFont,
            Font noteFont,
            Color headerBg,
            Color zebraBg,
            Color borderColor) throws DocumentException {

        // Colunas: Exercício | Séries | Repetições | RPE / Ritmo | Descanso | Execução Técnica e Segurança
        PdfPTable table = new PdfPTable(new float[]{2.3f, 0.7f, 1.0f, 1.1f, 0.85f, 3.05f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        table.setSpacingAfter(10);

        String[] headers = {"Exercício", "Séries", "Repetições", "RPE / Ritmo", "Descanso", "Execução Técnica e Segurança"};
        for (int i = 0; i < headers.length; i++) {
            PdfPCell cell = new PdfPCell(new Phrase(headers[i], headerFont));
            cell.setBackgroundColor(headerBg);
            cell.setBorderColor(borderColor);
            cell.setBorderWidth(0.5f);
            cell.setPadding(6);
            cell.setHorizontalAlignment((i == 0 || i == 5) ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell);
        }

        int idx = 0;
        for (TrainingExercise ex : exercises) {
            Color rowBg = (idx % 2 == 1) ? zebraBg : Color.WHITE;
            String numPrefix = (ex.getOrder() != null ? ex.getOrder() : (idx + 1)) + ". ";

            // Coluna 1: Nome do Exercício + Grupo Muscular / Equipamento
            Phrase namePhrase = new Phrase();
            namePhrase.add(new Chunk(numPrefix + safe(ex.getName()), exNameFont));

            StringBuilder subInfo = new StringBuilder();
            if (ex.getMuscleGroup() != null && !ex.getMuscleGroup().isBlank()) {
                subInfo.append(ex.getMuscleGroup());
            }
            if (ex.getEquipment() != null && !ex.getEquipment().isBlank()) {
                if (!subInfo.isEmpty()) subInfo.append(" • ");
                subInfo.append(ex.getEquipment());
            }
            if (!subInfo.isEmpty()) {
                namePhrase.add(new Chunk("\n" + subInfo, noteFont));
            }

            PdfPCell nameCell = new PdfPCell(namePhrase);
            styleBodyCell(nameCell, rowBg, borderColor, Element.ALIGN_LEFT);
            table.addCell(nameCell);

            // Coluna 2: Séries
            PdfPCell setsCell = new PdfPCell(new Phrase(safe(ex.getSets()), textFont));
            styleBodyCell(setsCell, rowBg, borderColor, Element.ALIGN_CENTER);
            table.addCell(setsCell);

            // Coluna 3: Repetições
            PdfPCell repsCell = new PdfPCell(new Phrase(safe(ex.getReps()), textFont));
            styleBodyCell(repsCell, rowBg, borderColor, Element.ALIGN_CENTER);
            table.addCell(repsCell);

            // Coluna 4: RPE (prioriza getRpe(), fallback getIntensity()) + Ritmo (getTempo())
            String rpeVal = (ex.getRpe() != null && !ex.getRpe().isBlank()) ? ex.getRpe() : ex.getIntensity();
            StringBuilder rpeTempo = new StringBuilder();
            if (rpeVal != null && !rpeVal.isBlank()) {
                rpeTempo.append(rpeVal.toUpperCase().startsWith("RPE") ? rpeVal : "RPE " + rpeVal);
            }
            if (ex.getTempo() != null && !ex.getTempo().isBlank()) {
                if (!rpeTempo.isEmpty()) rpeTempo.append("\n");
                rpeTempo.append("Ritmo: ").append(ex.getTempo());
            }
            PdfPCell rpeCell = new PdfPCell(new Phrase(rpeTempo.isEmpty() ? "-" : rpeTempo.toString(), textFont));
            styleBodyCell(rpeCell, rowBg, borderColor, Element.ALIGN_CENTER);
            table.addCell(rpeCell);

            // Coluna 5: Descanso
            PdfPCell restCell = new PdfPCell(new Phrase(safe(ex.getRest()), textFont));
            styleBodyCell(restCell, rowBg, borderColor, Element.ALIGN_CENTER);
            table.addCell(restCell);

            // Coluna 6: Execução Técnica e Notas Clínicas
            Phrase execPhrase = new Phrase();
            if (ex.getDetails() != null && !ex.getDetails().isBlank()) {
                execPhrase.add(new Chunk(ex.getDetails(), textFont));
            }
            if (ex.getNotas() != null && !ex.getNotas().isBlank()) {
                if (!execPhrase.isEmpty()) execPhrase.add(new Chunk("\n", textFont));
                execPhrase.add(new Chunk("Nota Clínica: " + ex.getNotas(), noteFont));
            }
            if (execPhrase.isEmpty()) {
                execPhrase.add(new Chunk("-", textFont));
            }
            PdfPCell execCell = new PdfPCell(execPhrase);
            styleBodyCell(execCell, rowBg, borderColor, Element.ALIGN_LEFT);
            table.addCell(execCell);

            idx++;
        }

        return table;
    }

    @Override
    public void styleBodyCell(PdfPCell cell, Color bg, Color borderColor, int align) {
        cell.setBackgroundColor(bg);
        cell.setBorderColor(borderColor);
        cell.setBorderWidth(0.5f);
        cell.setPadding(5.5f);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
    }

    @Override
    public String resolveStudentName(UserProfileRequest profile) {
        if (profile == null) return "";
        if (profile.getStudentName() != null && !profile.getStudentName().isBlank()) {
            return profile.getStudentName().trim();
        }
        if (profile.getStudentUsername() != null && !profile.getStudentUsername().isBlank()) {
            return profile.getStudentUsername().trim();
        }
        return "";
    }

    @Override
    public String buildMetaLine(UserProfileRequest profile) {
        if (profile == null) return "";
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (profile.getFrequencyPerWeek() != null && profile.getFrequencyPerWeek() > 0) {
            parts.add("Frequência: " + profile.getFrequencyPerWeek() + "x/semana");
        }
        if (profile.getDurationPerSession() != null && !profile.getDurationPerSession().isBlank()) {
            parts.add("Duração: " + profile.getDurationPerSession());
        }
        if (profile.getExerciseHistory() != null && !profile.getExerciseHistory().isBlank()) {
            parts.add("Nível: " + profile.getExerciseHistory());
        }
        if (profile.getObjective() != null && !profile.getObjective().isBlank()) {
            parts.add("Foco: " + profile.getObjective());
        }
        return String.join("  |  ", parts);
    }

    @Override
    public String safe(String val) {
        return (val != null && !val.isBlank()) ? val.trim() : "-";
    }

    // =========================================================================
    // CLASSE INTERNA DE PAGINAÇÃO ("Página X de Y" no rodapé de cada página)
    // =========================================================================
    private static class ClinicalPdfFooterEvent extends com.lowagie.text.pdf.PdfPageEventHelper {
        private final String leftText;
        private com.lowagie.text.pdf.PdfTemplate totalPagesTemplate;
        private com.lowagie.text.pdf.BaseFont baseFont;

        public ClinicalPdfFooterEvent(String leftText) {
            this.leftText = leftText;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalPagesTemplate = writer.getDirectContent().createTemplate(30, 12);
            try {
                baseFont = com.lowagie.text.pdf.BaseFont.createFont(
                        com.lowagie.text.pdf.BaseFont.HELVETICA,
                        com.lowagie.text.pdf.BaseFont.CP1252,
                        com.lowagie.text.pdf.BaseFont.NOT_EMBEDDED
                );
            } catch (Exception ignored) {
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            if (baseFont == null) return;
            com.lowagie.text.pdf.PdfContentByte cb = writer.getDirectContent();
            float y = document.bottom() - 18;

            // Linha separadora cinza clara
            cb.setColorStroke(new Color(203, 213, 225));
            cb.setLineWidth(0.5f);
            cb.moveTo(document.left(), y + 10);
            cb.lineTo(document.right(), y + 10);
            cb.stroke();

            cb.beginText();
            cb.setFontAndSize(baseFont, 7.5f);
            cb.setColorFill(new Color(100, 116, 139));

            // Texto à esquerda
            cb.setTextMatrix(document.left(), y);
            cb.showText(leftText);

            // Paginação à direita ("Página X de Y")
            String pageStr = "Página " + writer.getPageNumber() + " de ";
            float textWidth = baseFont.getWidthPoint(pageStr, 7.5f);
            float rightPos = document.right() - textWidth - 14;
            cb.setTextMatrix(rightPos, y);
            cb.showText(pageStr);
            cb.endText();

            cb.addTemplate(totalPagesTemplate, rightPos + textWidth, y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            if (baseFont == null) return;
            totalPagesTemplate.beginText();
            totalPagesTemplate.setFontAndSize(baseFont, 7.5f);
            totalPagesTemplate.setColorFill(new Color(100, 116, 139));
            totalPagesTemplate.showText(String.valueOf(writer.getPageNumber() - 1));
            totalPagesTemplate.endText();
        }
    }
}
