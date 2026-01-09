package com.aneto.registo_horas_service.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ProfileUploadService {
    String uploadImageAndSaveUrl(MultipartFile file, String username) throws IOException;

    void deleteOldImage(String s3Key);
}
