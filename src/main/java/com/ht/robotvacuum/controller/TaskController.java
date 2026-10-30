package com.ht.robotvacuum.controller;

import com.ht.robotvacuum.dto.response.ResGetTaskDTO;
import com.ht.robotvacuum.dto.response.ResPageDTO;
import com.ht.robotvacuum.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TaskController {

    private static final Set<String> SORT_FIELDS = Set.of("id", "startedAt", "finishedAt", "status");
    private final TaskService service;

    @GetMapping("/tasks/{id}")
    public ResponseEntity<ResGetTaskDTO> getById(@PathVariable long id, Authentication authentication) {
        return ResponseEntity.ok(service.getById(id, authentication));
    }

    @GetMapping("/tasks")
    public ResponseEntity<ResPageDTO<ResGetTaskDTO>> getAll(
            @RequestParam(required = false) Long robotId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam MultiValueMap<String, String> query, Authentication authentication) {
        var pageable = CrudPageRequest.of(page, size,
                query.getOrDefault("sort", List.of("id,asc")).toArray(String[]::new), SORT_FIELDS);
        if (robotId == null) {
            Assert.isTrue(!query.containsKey("robotId"), "Robot id must not be blank");
            return ResponseEntity.ok(service.getAll(pageable, authentication));
        }
        return ResponseEntity.ok(service.getByRobotId(robotId, pageable, authentication));
    }
}
