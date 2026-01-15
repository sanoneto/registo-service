package com.aneto.registo_horas_service.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class PredictHQEvent {
    private String id;
    private String title;
    private String start;
    private String category;
    private List<PredictHQEntity> entities;
    private List<String> location;
}