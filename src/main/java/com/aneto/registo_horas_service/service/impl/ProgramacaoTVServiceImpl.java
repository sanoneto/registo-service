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

@Service
@Slf4j // Se usares Lombok, ou usa o logger manual que criaste
public class ProgramacaoTVServiceImpl implements ProgramacaoTVService {

    private final JogoTVRepository repository;

    public ProgramacaoTVServiceImpl(JogoTVRepository repository) {
        this.repository = repository;
    }

    // 1. O CICLO (O "Círculo" que automatiza tudo)
    @Scheduled(fixedRate = 7200000) // Roda a cada 2 horas
    public void updateJob() {
        log.info("Iniciando tarefa agendada: Atualizando Base de Dados de Jogos TV");

        // Chamamos a função que faz o scraping
        List<JogoTV> novosJogos = extrairJogos();

        if (!novosJogos.isEmpty()) {
            repository.deleteAll(); // Remove jogos antigos
            repository.saveAll(novosJogos); // Guarda os novos na tabela
            log.info("Base de dados atualizada com {} novos jogos.", novosJogos.size());
        } else {
            log.warn("A extração não retornou jogos. A base de dados não foi alterada.");
        }
    }

    // 2. A LÓGICA DE SCRAPING (A tua função atual)
    @Override
    public List<JogoTV> extrairJogos() {
        List<JogoTV> listaJogos = new ArrayList<>();
        String url = "https://www.zerozero.pt/agenda.php";

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();

            Elements blocosJogos = doc.select(".zz-agenda-item");

            for (Element bloco : blocosJogos) {
                String equipas = bloco.select(".teams").text();
                String canal = bloco.select(".channel img").attr("title");
                String hora = bloco.select(".time").text();

                if (!equipas.isEmpty()) {
                    // Criamos o objeto da entidade
                    JogoTV jogo = new JogoTV();
                    jogo.setEquipas(equipas);
                    jogo.setCanal(canal);
                    jogo.setHora(hora);
                    listaJogos.add(jogo);
                }
            }
        } catch (IOException e) {
            log.error("Erro no scraping: {}", e.getMessage());
        }
        return listaJogos;
    }
}