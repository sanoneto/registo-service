package com.aneto.registo_horas_service.service.impl;


import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.aneto.registo_horas_service.service.ProfileUploadService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class ProfileUploadServiceImpl implements ProfileUploadService {
    private static final Logger log = LoggerFactory.getLogger(ProfileUploadServiceImpl.class);

    // Injetado automaticamente pelo Spring Cloud AWS
    private final AmazonS3 s3Client;

    private final TargetServiceClientImpl targetServiceClient;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    private static final String S3_FOLDER = "profile-pics/";

    @Override
    // Este é o método onde a exceção de permissão ocorre.
    public String uploadImageAndSaveUrl(MultipartFile file, String userName) {
        // Lógica para construir o nome da chave (caminho no S3)
        String fileExtension = getFileExtension(file.getOriginalFilename());
        String key = S3_FOLDER + userName + "." + fileExtension;

        // Configurações e Metadata
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());

        try (InputStream inputStream = file.getInputStream()) {
            // LINHA 52 (Provável local da chamada putObject, dependendo da formatação)
            // AQUI É ONDE O ERRO 's3:PutObject' OCORRE.
            PutObjectRequest putRequest = new PutObjectRequest(bucketName, key, inputStream, metadata);

            s3Client.putObject(putRequest);

            String publicUrl = s3Client.getUrl(bucketName, key).toString();
            targetServiceClient.updateProfilePicUrl(userName, publicUrl);

            log.info("Imagem {} carregada com sucesso para o bucket {}.", key, bucketName);
            log.info("publicUrl: {} ", publicUrl );
            // Construir a URL pública
            return publicUrl;

        } catch (IOException e) {
            // Tratar erro de I/O do arquivo
            throw new RuntimeException("Falha ao ler o arquivo de imagem.", e);
        }
    }

    // Método auxiliar (Simplificado para o exemplo)
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "jpg"; // default
        }
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex > 0) ? filename.substring(dotIndex + 1) : "jpg";
    }

    @Override
    public void deleteOldImage(String s3Key) {
        if (s3Client.doesObjectExist(bucketName, s3Key)) {
            s3Client.deleteObject(bucketName, s3Key);
        }
    }
}
