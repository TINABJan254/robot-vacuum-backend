package com.ht.robotvacuum.controller;

import com.ht.robotvacuum.dto.response.*;
import com.ht.robotvacuum.model.Alert;
import com.ht.robotvacuum.service.AlertService;
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
public class AlertController {

    private static final Set<String> SORT_FIELDS = Set.of("id", "type", "readAt", "createdAt");
    private final AlertService service;

    @PostMapping("/alerts")
    public ResponseEntity<ResCreateAlertDTO> create(@Valid @RequestBody Alert input, Authentication authentication) {
        var created = service.create(input, authentication);
        return ResponseEntity.created(URI.create("/api/v1/alerts/" + created.id())).body(created);
    }

    @GetMapping("/alerts/{id}")
    public ResponseEntity<ResGetAlertDTO> getById(@PathVariable long id, Authentication authentication) {
        return ResponseEntity.ok(service.getById(id, authentication));
    }

    @GetMapping("/alerts")
    public ResponseEntity<ResPageDTO<ResGetAlertDTO>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam MultiValueMap<String, String> query, Authentication authentication) {
        var pageable = CrudPageRequest.of(page, size,
                query.getOrDefault("sort", List.of("id,asc")).toArray(String[]::new), SORT_FIELDS);
        return ResponseEntity.ok(service.getAll(pageable, authentication));
    }

    @PutMapping("/alerts/{id}")
    public ResponseEntity<ResUpdateAlertDTO> update(@PathVariable long id,
            @Valid @RequestBody Alert input, Authentication authentication) {
        input.setId(id);
        return ResponseEntity.ok(service.update(input, authentication));
    }

    @DeleteMapping("/alerts/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable long id, Authentication authentication) {
        service.deleteById(id, authentication);
        return ResponseEntity.noContent().build();
    }
}
