package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.service.LogService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class LogServiceImpl  implements LogService {

    private static final String LOG_PATH = "logs/app.log";

    public List<String> getInventoryAlerts() throws IOException {
        Path path = Paths.get(LOG_PATH);
        if (!Files.exists(path)) {
            return List.of("Ficheiro de log não encontrado.");
        }

        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            List<String> filtered = lines
                    .filter(line ->
                            line.contains("[ALERTA DE INVENTÁRIO]") ||
                                    line.contains("Geração concluída com sucesso") ||
                                    line.contains("Nome normalizado de")
                    )
                    .collect(Collectors.toList());

            Collections.reverse(filtered);
            return filtered;
        }
    }

    public void clearLogs() throws IOException {
        Path path = Paths.get(LOG_PATH);
        if (Files.exists(path)) {
            Files.write(path, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
        }
    }
}