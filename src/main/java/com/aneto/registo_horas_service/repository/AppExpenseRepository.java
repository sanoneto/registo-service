package com.aneto.registo_horas_service.repository;




import com.aneto.registo_horas_service.models.AppExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppExpenseRepository extends JpaRepository<AppExpense, Long> {
    // Aqui podes adicionar métodos de busca por data no futuro
}