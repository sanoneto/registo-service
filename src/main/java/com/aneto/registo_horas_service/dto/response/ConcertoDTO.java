package com.aneto.registo_horas_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConcertoDTO {
    private String id;
    private String name;
    private String date;
    private String time;
    private String venue;
    private String city;
    private String category;
}