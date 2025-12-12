// language: java
package com.aneto.registo_horas_service.service;


import com.aneto.registo_horas_service.dto.request.RegisterRequest;
import com.aneto.registo_horas_service.dto.response.MonthlySummary;
import com.aneto.registo_horas_service.dto.response.PageResponse;
import com.aneto.registo_horas_service.dto.response.PerfilResponse;
import com.aneto.registo_horas_service.dto.response.RegisterResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RegistosHorasService {

    // Cria/Atualiza um registro (calcula as horas)
    RegisterResponse submeterHoras(RegisterRequest request, String username);

    // Retorna todos os registros (somente ADMIN)
    List<RegisterResponse> findAllRegisteredHours();

    // Retorna registros por usuário
    List<RegisterResponse> buscarRegistrosPorUsuario(String username);

    // Atualiza um registro existente
    RegisterResponse atualizarRegistro(UUID publicId, RegisterRequest request,String username)throws Exception;

    // Deleta um registro
    void deleteRegistry(UUID publicId);

    double getTotalHorasPorUsuarioProjrct(String username , String project_name);

    List<PerfilResponse>  findTotalHoursAndRequiredHoursByUserName(@Param("username") String name );

    List<MonthlySummary> findMonthlySummary(@Param("username") String name );

    PageResponse<RegisterResponse> findAllRegisteredHoursUserProjectName(String name,String projectName, Pageable pageable);
    PageResponse<RegisterResponse> findAllRegisteredHoursPageProjectName(String projectName,Pageable pageable);


}
