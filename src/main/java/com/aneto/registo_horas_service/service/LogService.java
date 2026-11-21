package com.aneto.registo_horas_service.service;

import java.io.IOException;
import java.util.List;

public interface LogService {
    List<String> getInventoryAlerts() throws IOException;
    void clearLogs() throws IOException;
}
