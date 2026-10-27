package com.ht.robotvacuum.controller;

import com.ht.robotvacuum.dto.response.*;
import com.ht.robotvacuum.dto.request.ReqPairRobotDTO;
import com.ht.robotvacuum.model.Robot;
import com.ht.robotvacuum.service.RobotService;
import com.ht.robotvacuum.service.RobotStateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
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

    private static final Set<String> SORT_FIELDS = Set.of("id", "name");
    private final RobotService service;
    private final RobotStateService stateService;

    @PostMapping("/robots")
    public ResponseEntity<ResCreateRobotDTO> create(@Valid @RequestBody Robot input,
                                                       Authentication authentication) {
        var created = service.create(input, authentication);
        return ResponseEntity.created(URI.create("/api/v1/robots/" + created.id())).body(created);
    }

    @PostMapping("/robots/pair")
    public ResponseEntity<ResGetRobotDTO> pair(@Valid @RequestBody ReqPairRobotDTO input,
                                              Authentication authentication) {

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.pair(input, authentication));
    }

    @PostMapping("/robots/{id}/unpair")
    public ResponseEntity<ResGetRobotDTO> unpair(@PathVariable long id, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.unpair(id, authentication));
    }

    @GetMapping("/robots/{id}")
    public ResponseEntity<ResGetRobotDTO> getById(@PathVariable long id, Authentication authentication) {
        return ResponseEntity.ok(service.getById(id, authentication));
    }

    @GetMapping("/robots/{id}/telemetry")
    public ResponseEntity<RobotTelemetrySnapshot> getTelemetry(@PathVariable long id,
                                                              Authentication authentication) {
        return stateService.getLatest(id, authentication)
                .map(snapshot -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(snapshot))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
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
            @RequestBody Robot input, Authentication authentication) {
        input.setId(id);
        return ResponseEntity.ok(service.update(input, authentication));
    }

    @DeleteMapping("/robots/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable long id, Authentication authentication) {
        service.deleteById(id, authentication);
        return ResponseEntity.noContent().build();
    }
}
