package com.aneto.registo_horas_service.models.Training;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Gender {
    MALE,
    FEMALE,
    OTHER;

    @JsonCreator
    public static Gender fromString(String value) {
        if (value == null) return null;
        return switch (value.toUpperCase()) {
            case "MASCULINO", "MALE" -> MALE;
            case "FEMININO", "FEMALE" -> FEMALE;
            case "OUTRO", "OTHER" -> OTHER;
            default -> throw new IllegalArgumentException("Género inválido: " + value);
        };
    }
}