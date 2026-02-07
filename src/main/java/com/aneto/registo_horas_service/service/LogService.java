package com.aneto.registo_horas_service.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
@Service
public class LogService {

    private final String LOG_PATH = "logs/app.log";

    public List<String> getInventoryAlerts() throws IOException {
        Path path = Paths.get(LOG_PATH);
        if (!Files.exists(path)) return List.of("Ficheiro de log não encontrado.");

        try (Stream<String> lines = Files.lines(path)) {
            return lines.filter(line ->
                            line.contains("[ALERTA DE INVENTÁRIO]") ||
                                    line.contains("Geração concluída com sucesso") ||
                                    line.contains("Nome normalizado de")
                    )
                    .collect(Collectors.toList());
        }
    }

    public void clearLogs() throws IOException {
        Path path = Paths.get(LOG_PATH);
        if (Files.exists(path)) {
            Files.write(path, new byte[0]); // Limpa o ficheiro sem o apagar
        }
    }
}