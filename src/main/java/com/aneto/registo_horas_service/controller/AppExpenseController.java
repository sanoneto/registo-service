package com.aneto.registo_horas_service.controller;


import com.aneto.registo_horas_service.models.AppExpense;
import com.aneto.registo_horas_service.repository.AppExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/expenses")
@RequiredArgsConstructor
public class AppExpenseController {


    private final AppExpenseRepository repository;

    // LISTAR TODOS OS GASTOS
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<AppExpense> getAllExpenses() {
        return repository.findAll();
    }

    // CRIAR NOVO GASTO
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppExpense> createExpense(@RequestBody AppExpense expense) {
        if (expense.getDate() == null) {
            expense.setDate(LocalDate.now());
        }
        return ResponseEntity.ok(repository.save(expense));
    }

    // APAGAR GASTO
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}