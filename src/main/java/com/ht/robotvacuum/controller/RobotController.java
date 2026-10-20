package com.ht.robotvacuum.controller;

import com.ht.robotvacuum.dto.response.*;
import com.ht.robotvacuum.model.Robot;
import com.ht.robotvacuum.service.RobotService;
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
public class RobotController {

    private static final Set<String> SORT_FIELDS = Set.of("id", "name", "pairCode");
    private final RobotService service;

    @PostMapping("/robots")
    public ResponseEntity<ResCreateRobotDTO> create(@Valid @RequestBody Robot input,
                                                       Authentication authentication) {
        var created = service.create(input, authentication);
        return ResponseEntity.created(URI.create("/api/v1/robots/" + created.id())).body(created);
    }

    @GetMapping("/robots/{id}")
    public ResponseEntity<ResGetRobotDTO> getById(@PathVariable long id, Authentication authentication) {
        return ResponseEntity.ok(service.getById(id, authentication));
    }

    @GetMapping("/robots")
    public ResponseEntity<ResPageDTO<ResGetRobotDTO>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam MultiValueMap<String, String> query, Authentication authentication) {
        var pageable = CrudPageRequest.of(page, size,
                query.getOrDefault("sort", List.of("id,asc")).toArray(String[]::new), SORT_FIELDS);
        return ResponseEntity.ok(service.getAll(pageable, authentication));
    }

    @PutMapping("/robots/{id}")
    public ResponseEntity<ResUpdateRobotDTO> update(@PathVariable long id,
            @Valid @RequestBody Robot input, Authentication authentication) {
        input.setId(id);
        return ResponseEntity.ok(service.update(input, authentication));
    }

    @DeleteMapping("/robots/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable long id, Authentication authentication) {
        service.deleteById(id, authentication);
        return ResponseEntity.noContent().build();
    }
}
