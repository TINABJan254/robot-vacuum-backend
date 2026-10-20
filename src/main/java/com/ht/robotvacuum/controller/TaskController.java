package com.ht.robotvacuum.controller;

import com.ht.robotvacuum.dto.response.*;
import com.ht.robotvacuum.model.Task;
import com.ht.robotvacuum.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TaskController {

    private static final Set<String> SORT_FIELDS = Set.of("id", "name", "startAt", "duration", "status", "createdAt");
    private final TaskService service;

    @PostMapping("/tasks")
    public ResponseEntity<ResCreateTaskDTO> create(@Valid @RequestBody Task input,
                                                       Authentication authentication) {
        var created = service.create(input, authentication);
        return ResponseEntity.created(URI.create("/api/v1/tasks/" + created.id())).body(created);
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<ResGetTaskDTO> getById(@PathVariable long id, Authentication authentication) {
        return ResponseEntity.ok(service.getById(id, authentication));
    }

    @GetMapping("/tasks")
    public ResponseEntity<ResPageDTO<ResGetTaskDTO>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam MultiValueMap<String, String> query, Authentication authentication) {
        var pageable = CrudPageRequest.of(page, size,
                query.getOrDefault("sort", List.of("id,asc")).toArray(String[]::new), SORT_FIELDS);
        return ResponseEntity.ok(service.getAll(pageable, authentication));
    }

    @PutMapping("/tasks/{id}")
    public ResponseEntity<ResUpdateTaskDTO> update(@PathVariable long id,
            @Valid @RequestBody Task input, Authentication authentication) {
        input.setId(id);
        return ResponseEntity.ok(service.update(input, authentication));
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable long id, Authentication authentication) {
        service.deleteById(id, authentication);
        return ResponseEntity.noContent().build();
    }
}
