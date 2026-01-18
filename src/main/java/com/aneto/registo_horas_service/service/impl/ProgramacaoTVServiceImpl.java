package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.models.JogoTV;
import com.aneto.registo_horas_service.repository.JogoTVRepository;
import com.aneto.registo_horas_service.service.ProgramacaoTVService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@Slf4j // Se usares Lombok, ou usa o logger manual que criaste
public class ProgramacaoTVServiceImpl implements ProgramacaoTVService {

    private final JogoTVRepository repository;

    public ProgramacaoTVServiceImpl(JogoTVRepository repository) {
        this.repository = repository;
    }

    // 1. O CICLO (O "Círculo" que automatiza tudo)
    @Scheduled(fixedRate = 7200000)
    public void updateJob() {
        log.info("Iniciando atualização da programação de TV...");

        List<JogoTV> novosJogos = extrairJogos();

        // SÓ APAGA SE TIVERMOS DADOS NOVOS (Fallback de Segurança)
        if (novosJogos != null && !novosJogos.isEmpty()) {
            repository.deleteAll();
            repository.saveAll(novosJogos);
            log.info("Base de dados atualizada com sucesso.");
        } else {
            log.error("Não foi possível obter novos dados. Mantendo os dados anteriores para evitar blackout.");
        }
    }

    @Override
    public List<JogoTV> extrairJogos() {
        List<JogoTV> listaJogos = new ArrayList<>();
        // URL específica de TV
        String url = "https://www.zerozero.pt/tv.php";

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(getRandomUserAgent()) // Disfarce variável
                    .header("Accept-Language", "pt-PT,pt;q=0.9")
                    .timeout(20000) // Mais tempo para responder
                    .get();

            // O seletor para a página /tv.php costuma ser as linhas da tabela de jogos
            Elements linhas = doc.select(".box.agenda-tv table tr");

            for (Element linha : linhas) {
                String canal = linha.select(".canal img").attr("title").toUpperCase();

                // Filtro direto para o que pediste
                if (canal.contains("SPORT TV") || canal.contains("BENFICA TV") || canal.contains("DAZN")) {
                    String equipas = linha.select(".home, .away").text(); // Captura as duas equipas
                    String hora = linha.select(".hora").text();

                    if (!equipas.isEmpty()) {
                        JogoTV jogo = new JogoTV();
                        jogo.setEquipas(equipas.replace(" ", " vs "));
                        jogo.setCanal(canal);
                        jogo.setHora(hora);
                        listaJogos.add(jogo);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Erro crítico no scraping: {}", e.getMessage());
        }
        return listaJogos;
    }

    private String getRandomUserAgent() {
        String[] agents = {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/13.1.3",
                "Mozilla/5.0 (X11; Linux x86_64) Firefox/121.0"
        };
        return agents[new Random().nextInt(agents.length)];
    }
}