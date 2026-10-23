package com.ht.robotvacuum.controller;

import com.ht.robotvacuum.dto.request.ReqRobotCommandDTO;
import com.ht.robotvacuum.dto.response.ResRobotCommandDTO;
import com.ht.robotvacuum.service.RobotCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RobotCommandController {

    private final RobotCommandService robotCommandServiceservice;

    @PostMapping("/robots/{id}/commands")
    public ResponseEntity<ResRobotCommandDTO> send(@PathVariable long id,
            @Valid @RequestBody ReqRobotCommandDTO input, Authentication authentication) {

        return ResponseEntity.accepted().header("Cache-Control", "no-store")
                .body(robotCommandServiceservice.send(id, input, authentication));
    }
}
