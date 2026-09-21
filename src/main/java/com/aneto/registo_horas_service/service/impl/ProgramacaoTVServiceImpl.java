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
        // Alternativa: Verifique se /agenda.php ou /zapping.php funciona melhor no momento
        String url = "https://www.zerozero.pt/zapping.php";

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(getRandomUserAgent())
                    .header("Accept-Language", "pt-PT,pt;q=0.9,en;q=0.8")
                    .header("Referer", "https://www.google.com/") // Ajuda a evitar o 404/403
                    .timeout(30000)
                    .get();

            // O seletor do zerozero para jogos na TV geralmente usa a classe .zz-agenda-jogo ou tabelas dentro de .box
            // É importante inspecionar o HTML atual, pois eles mudam as classes frequentemente.
            Elements linhas = doc.select("table tr");

            for (Element linha : linhas) {
                // Tenta obter o título do canal da imagem ou do texto
                String canal = linha.select("td.canal img").attr("alt").toUpperCase();
                if (canal.isEmpty()) {
                    canal = linha.select("td.canal").text().toUpperCase();
                }

                if (isCanalDesejado(canal)) {
                    String equipas = linha.select("td.jogo").text();
                    String hora = linha.select("td.hora").text();

                    if (!equipas.isEmpty()) {
                        JogoTV jogo = new JogoTV();
                        jogo.setEquipas(equipas.trim());
                        jogo.setCanal(canal);
                        jogo.setHora(hora);
                        listaJogos.add(jogo);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Erro de conexão (Página não encontrada ou bloqueada): {}", e.getMessage());
        } catch (Exception e) {
            log.error("Erro inesperado no scraping: {}", e.getMessage());
        }
        return listaJogos;
    }

    // Limpa o código principal
    private boolean isCanalDesejado(String canal) {
        return canal.contains("SPORT TV") || canal.contains("BENFICA TV") || canal.contains("DAZN") || canal.contains("ELEVEN");
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