package com.aneto.registo_horas_service.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

@Configuration
public class GoogleAiConfig {

    private static final String GEMINI_MODEL = "gemini-2.5-flash"; // Nota: 1.5 é a versão estável atual

    @Value("${gcp.project.id}")
    private String projectId;

    @Value("${gcp.project.location}")
    private String location;

    @Value("${gcp.credentials.json-content}")
    private String jsonContent;

    @Bean
    public GenerativeModel generativeModel() throws IOException {
        if (jsonContent == null || jsonContent.isEmpty()) {
            throw new IllegalStateException("A propriedade gcp.credentials.json-content está vazia!");
        }

        String fixedJson = jsonContent.replace("\\\\n", "\\n");

        try (ByteArrayInputStream is = new ByteArrayInputStream(fixedJson.getBytes(StandardCharsets.UTF_8))) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(is)
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));

            VertexAI vertexAI = new VertexAI.Builder()
                    .setProjectId(projectId)
                    .setLocation(location)
                    .setCredentials(credentials)
                    .build();

            GenerationConfig config = GenerationConfig.newBuilder()
                    .setTemperature(0.2f)
                    .build();

            return new GenerativeModel.Builder()
                    .setModelName(GEMINI_MODEL)
                    .setVertexAi(vertexAI)
                    .setGenerationConfig(config)
                    .build();
        }
    }
}