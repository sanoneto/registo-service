package com.aneto.registo_horas_service.mapper;

import com.aneto.registo_horas_service.dto.request.PlanoRequestDTO;
import com.aneto.registo_horas_service.dto.response.PlanoResponseDTO;
import com.aneto.registo_horas_service.models.Plano;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring") // Define como um Bean do Spring
public interface PlanoMapper {

    PlanoMapper INSTANCE = Mappers.getMapper(PlanoMapper.class);

    // Converte o Request (vinda do usuário) para a Entidade (banco)
    Plano toEntity(PlanoRequestDTO requestDTO);

    // Converte a Entidade (banco) para o Response (saída)
    // Note que os campos ausentes no DTO serão ignorados automaticamente
    PlanoResponseDTO toResponse(Plano plano);
}