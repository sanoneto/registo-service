package com.aneto.registo_horas_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Slf4j
@Service // ESSENCIAL para o Spring encontrar a classe
public class TelegramBotService {

    private final TelegramClient telegramClient;

    public TelegramBotService(@Value("${telegram.bot.token.anetoBot}") String botToken) {
        // Inicializa o cliente para poder usar o .execute()
        this.telegramClient = new OkHttpTelegramClient(botToken);
    }

    public void enviarMensagem(SendMessage message) {
        try {
            telegramClient.execute(message); // Aqui o execute() funciona!
        } catch (TelegramApiException e) {
            if (e.getMessage().contains("bot was blocked by the user")) {
                log.warn("⚠️ O utilizador com ChatID {} bloqueou o bot. Devemos remover o vínculo.", message.getChatId());
                // Aqui você poderia chamar um método para limpar o telegramChatId no DB
            } else {
                log.error("❌ Erro de API do Telegram: {}", e.getMessage());
            }
        }
    }

}