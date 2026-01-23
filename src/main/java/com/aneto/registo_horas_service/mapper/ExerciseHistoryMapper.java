package com.aneto.registo_horas_service.mapper;

import com.aneto.registo_horas_service.dto.response.ExerciseHistoryResponse;
import com.aneto.registo_horas_service.models.ExerciseHistoryEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ExerciseHistoryMapper {

    // 1. Mapeamento para um único objeto
    @Mapping(target = "date", source = "registeredAt", qualifiedByName = "formatarData")
    // @Mapping(target = "name", source = "exerciseName") // Ajusta para o campo correto da Entity
    ExerciseHistoryResponse toResponse(ExerciseHistoryEntity entity);

    // 2. MapStruct gera automaticamente o mapeamento de listas usando o método acima
    List<ExerciseHistoryResponse> toResponseList(List<ExerciseHistoryEntity> entities);

    @Named("formatarData")
    default String formatarData(String registeredAt) {
        if (registeredAt == null) return null;
        try {
            ZonedDateTime dataUtc = ZonedDateTime.parse(registeredAt);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            return dataUtc.format(formatter);
        } catch (Exception e) {
            return registeredAt; // Fallback caso a string não seja ISO
        }
    }
}

