package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.dto.response.UploadResponse;
import com.aneto.registo_horas_service.service.ProfileUploadService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Objects;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProfileUploadController {
    private static final Logger log = LoggerFactory.getLogger(ProfileUploadController.class);
    private final ProfileUploadService profileUploadService; // Injetar o serviço

    // Header injetado pelo Gateway
    private static final String X_USER_ID = "X-User-Id";

    @PreAuthorize("hasRole('ADMIN') or hasRole('ESPECIALISTA') or (hasRole('ESTAGIARIO') or hasRole('USER')  and #username == authentication.name)")
    @PostMapping("/upload-profile-pic")
    public ResponseEntity<UploadResponse> uploadProfilePic(
            @RequestParam("profilePicture") MultipartFile file,
            @RequestParam("userName") String username
    ) {
        // 1. Validações (mantidas do código original)
        if (file.isEmpty() || file.getSize() == 0) {
            return new ResponseEntity<>(
                    new UploadResponse(null, "O ficheiro de imagem não pode estar vazio."),
                    HttpStatus.BAD_REQUEST
            );
        }
        if (!Objects.requireNonNull(file.getContentType()).startsWith("image/")) {
            return new ResponseEntity<>(
                    new UploadResponse(null, "Formato de ficheiro não suportado. Use uma imagem."),
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE
            );
        }
        // 2. Upload para a Cloud via Service
        try {
            // Delega a lógica de upload para o S3 e de atualização da DB ao serviço
            String publicUrl = profileUploadService.uploadImageAndSaveUrl(file, username);

            // 3. Retorno de Sucesso
            // O publicUrl é o link completo do S3 (ex: https://seu-bucket.s3.us-east-1.amazonaws.com/...)
            log.info("Foto de perfil atualizada com sucesso! publicUrl: {}", publicUrl);
            return new ResponseEntity<>(
                    new UploadResponse(publicUrl, "Foto de perfil atualizada com sucesso!"),
                    HttpStatus.OK
            );

        } catch (IOException e) {
            // Erro ao ler o ficheiro ou ao fazer o upload para o S3
            System.err.println("Erro ao fazer upload para S3: " + e.getMessage());
            return new ResponseEntity<>(
                    new UploadResponse(null, "Erro interno do servidor ao guardar o ficheiro na nuvem."),
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}