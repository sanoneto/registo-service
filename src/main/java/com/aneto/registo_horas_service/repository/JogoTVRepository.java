package com.aneto.registo_horas_service.repository;

import com.aneto.registo_horas_service.models.JogoTV;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JogoTVRepository extends JpaRepository<JogoTV, Long> {
    // Aqui já tens métodos como saveAll(), findAll(), deleteAll() prontos a usar
}