package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.models.Training.Exercises;
import com.aneto.registo_horas_service.repository.ExerciseRepository;
import com.aneto.registo_horas_service.service.ExerciseVideoService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/exercises")
public class ExerciseAdminController {

    private final ExerciseRepository repository;
    private final ExerciseVideoService videoService;

    public ExerciseAdminController(ExerciseRepository repository, ExerciseVideoService videoService) {
        this.repository = repository;
        this.videoService = videoService;
    }

    @GetMapping
    public List<Exercises> getAll() {
        return repository.findAll();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Exercises> create(@RequestBody Exercises exercise) {
        Exercises saved = repository.save(exercise);
        // Garantir que o cache esteja limpo caso houvesse um fallback negativo antes
        videoService.evictCache(saved.getName());
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/bulk")
    @Transactional
    public ResponseEntity<List<Exercises>> createBulk(@RequestBody List<Exercises> exercises) {
        List<Exercises> savedList = repository.saveAll(exercises);
        // Para bulk, podemos limpar o cache individualmente ou todo o namespace
        savedList.forEach(e -> videoService.evictCache(e.getName()));
        return ResponseEntity.ok(savedList);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Exercises> update(@PathVariable Long id, @RequestBody Exercises details) {
        return repository.findById(id)
                .map(exercise -> {
                    // Guardamos o nome antigo para limpar o cache se o nome mudar
                    String oldName = exercise.getName();

                    exercise.setName(details.getName());
                    exercise.setVideoUrl(details.getVideoUrl());
                    exercise.setCategory(details.getCategory());

                    Exercises updated = repository.save(exercise);

                    // Limpamos o cache do nome antigo e do novo
                    videoService.evictCache(oldName);
                    videoService.evictCache(updated.getName());

                    return ResponseEntity.ok(updated);
                }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repository.findById(id).ifPresent(exercise -> {
            videoService.evictCache(exercise.getName());
            repository.delete(exercise);
        });
        return ResponseEntity.noContent().build();
    }
}