package com.aneto.registo_horas_service.service;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.TrainingExercise;
import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

import java.awt.*;
import java.util.List;

public interface PdfService {
    byte[] generatePdfBytes(TrainingPlanResponse plan);
    PdfPTable buildClinicalTable(
            List<TrainingExercise> exercises,
            Font headerFont,
            Font exNameFont,
            Font textFont,
            Font noteFont,
            Color headerBg,
            Color zebraBg,
            Color borderColor) throws DocumentException;
    void styleBodyCell(PdfPCell cell, Color bg, Color borderColor, int align);
    String resolveStudentName(UserProfileRequest profile);
    String buildMetaLine(UserProfileRequest profile);
    String safe(String val);
}
