package com.aneto.registo_horas_service.service.impl;


import com.aneto.registo_horas_service.dto.response.PlanoPagamentoDTO;
import com.aneto.registo_horas_service.dto.response.RegistoTreinoDTO;
import com.aneto.registo_horas_service.service.ComprovativoAssinaturaPdfService;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

@Service
public class ComprovativoAssinaturaPdfServiceImpl implements ComprovativoAssinaturaPdfService {

    @Override
    public byte[] gerarComprovativoPdf(PlanoPagamentoDTO plano, List<RegistoTreinoDTO> treinos) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            Color ciano = new Color(0, 180, 216);
            Color escuro = new Color(15, 23, 42);
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, escuro);
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, ciano);
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.DARK_GRAY);
            Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
            Font smallMuted = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

            // Cabeçalho
            Paragraph title = new Paragraph("ProAct — Comprovativo Digital de Plano & Treinos", titleFont);
            title.setSpacingAfter(4);
            document.add(title);

            String emitidoEm = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            Paragraph sub = new Paragraph("Documento assinado digitalmente • Emitido em " + emitidoEm, subtitleFont);
            sub.setSpacingAfter(16);
            document.add(sub);

            // Bloco de Informação do Sócio e do Pack
            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);
            infoTable.setSpacingAfter(14);

            addInfoRow(infoTable, "Sócio:", plano.getNomeSocio() + " (" + plano.getNoSocio() + ")", labelFont, valueFont);
            addInfoRow(infoTable, "Pack Contratado:", plano.getPackName(), labelFont, valueFont);
            addInfoRow(infoTable, "Data de Início:", String.valueOf(plano.getDataPack()), labelFont, valueFont);
            addInfoRow(infoTable, "Valor / Total Aulas:", plano.getValor() + " € (" + plano.getAulasPack() + " aulas)", labelFont, valueFont);

            int aulasUsadas = treinos != null ? treinos.size() : 0;
            String saldoTexto = "PACK".equalsIgnoreCase(plano.getTipoPack())
                    ? Math.max(0, plano.getAulasPack() - aulasUsadas) + " aulas restantes"
                    : "Acesso Livre";
            addInfoRow(infoTable, "Saldo Atual:", saldoTexto, labelFont, valueFont);

            document.add(infoTable);

            // Assinatura de Adesão ao Plano
            if (plano.getAssinatura() != null && !plano.getAssinatura().isBlank()) {
                Paragraph sigTitle = new Paragraph("Assinatura de Adesão ao Plano:", labelFont);
                sigTitle.setSpacingBefore(6);
                sigTitle.setSpacingAfter(6);
                document.add(sigTitle);

                Image sigImg = decodeBase64Image(plano.getAssinatura(), 180, 65);
                if (sigImg != null) {
                    document.add(sigImg);
                }
            }

            // Tabela de Sessões de Treino Assinadas
            if (treinos != null && !treinos.isEmpty()) {
                Paragraph histTitle = new Paragraph("Histórico de Sessões Assinadas (" + treinos.size() + ")", subtitleFont);
                histTitle.setSpacingBefore(18);
                histTitle.setSpacingAfter(8);
                document.add(histTitle);

                PdfPTable table = new PdfPTable(new float[]{2f, 1.5f, 3f, 2.5f});
                table.setWidthPercentage(100);

                String[] headers = {"Data", "Hora", "Pack", "Assinatura Sócio"};
                for (String h : headers) {
                    PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE)));
                    cell.setBackgroundColor(escuro);
                    cell.setPadding(6);
                    table.addCell(cell);
                }

                for (RegistoTreinoDTO t : treinos) {
                    PdfPCell cData = new PdfPCell(new Phrase(String.valueOf(t.getData()), valueFont));
                    cData.setPadding(5);
                    table.addCell(cData);

                    PdfPCell cHora = new PdfPCell(new Phrase(t.getHora(), valueFont));
                    cHora.setPadding(5);
                    table.addCell(cHora);

                    PdfPCell cPack = new PdfPCell(new Phrase(t.getPackName() != null ? t.getPackName() : plano.getPackName(), valueFont));
                    cPack.setPadding(5);
                    table.addCell(cPack);

                    PdfPCell sigCell = new PdfPCell();
                    sigCell.setPadding(4);
                    Image rowSig = decodeBase64Image(t.getAssinatura(), 95, 32);
                    if (rowSig != null) {
                        sigCell.addElement(rowSig);
                    } else {
                        sigCell.addElement(new Phrase("Assinado", valueFont));
                    }
                    table.addCell(sigCell);
                }

                document.add(table);
            }

            Paragraph footer = new Paragraph("\nProAct Ecosystem • Work smart. Live fit.", smallMuted);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar PDF do comprovativo assinado", e);
        }
    }

    private void addInfoRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell c1 = new PdfPCell(new Phrase(label, labelFont));
        c1.setBorder(Rectangle.NO_BORDER);
        c1.setPadding(4);
        PdfPCell c2 = new PdfPCell(new Phrase(value != null ? value : "-", valueFont));
        c2.setBorder(Rectangle.NO_BORDER);
        c2.setPadding(4);
        table.addCell(c1);
        table.addCell(c2);
    }

    private Image decodeBase64Image(String base64DataUrl, float maxWidth, float maxHeight) {
        try {
            String base64Clean = base64DataUrl.contains(",")
                    ? base64DataUrl.substring(base64DataUrl.indexOf(",") + 1)
                    : base64DataUrl;
            byte[] imageBytes = Base64.getDecoder().decode(base64Clean);
            Image img = Image.getInstance(imageBytes);
            img.scaleToFit(maxWidth, maxHeight);
            return img;
        } catch (Exception e) {
            return null;
        }
    }
}