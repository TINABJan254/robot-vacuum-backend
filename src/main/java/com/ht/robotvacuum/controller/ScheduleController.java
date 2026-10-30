package com.ht.robotvacuum.controller;

import com.ht.robotvacuum.dto.request.ReqCreateScheduleDTO;
import com.ht.robotvacuum.dto.request.ReqUpdateScheduleDTO;
import com.ht.robotvacuum.dto.response.ResGetScheduleDTO;
import com.ht.robotvacuum.dto.response.ResPageDTO;
import com.ht.robotvacuum.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ScheduleController {

    private static final Set<String> SORT_FIELDS = Set.of("id", "time", "enabled");
    private final ScheduleService scheduleService;

    @PostMapping("/schedules")
    public ResponseEntity<ResGetScheduleDTO> create(@Valid @RequestBody ReqCreateScheduleDTO input,
                                                   Authentication authentication) {
        var created = scheduleService.create(input.robotId(), input.time(), input.enabled(),
                input.daysOfWeek(), authentication);

        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(created);
    }

    @PatchMapping("/schedules/{id}")
    public ResponseEntity<ResGetScheduleDTO> update(@PathVariable long id,
            @Valid @RequestBody ReqUpdateScheduleDTO input, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(scheduleService.update(id, input.time(), input.enabled(), input.daysOfWeek(), authentication));
    }

    @DeleteMapping("/schedules/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable long id, Authentication authentication) {
        scheduleService.deleteById(id, authentication);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/schedules")
    public ResponseEntity<ResPageDTO<ResGetScheduleDTO>> getByRobotId(
            @RequestParam long robotId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam MultiValueMap<String, String> query, Authentication authentication) {
        var pageable = CrudPageRequest.of(page, size,
                query.getOrDefault("sort", List.of("id,asc")).toArray(String[]::new), SORT_FIELDS);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(scheduleService.getByRobotId(robotId, pageable, authentication));
    }
}
