package com.ht.robotvacuum.controller;

import com.ht.robotvacuum.dto.response.*;
import com.ht.robotvacuum.model.User;
import com.ht.robotvacuum.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.Assert;
import java.nio.charset.StandardCharsets;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private static final Set<String> SORT_FIELDS = Set.of("id", "name", "username", "role");
    private final UserService service;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/users")
    public ResponseEntity<ResCreateUserDTO> create(@Valid @RequestBody User input,
                                                       Authentication authentication) {
        Assert.isTrue(input.getPassword().getBytes(StandardCharsets.UTF_8).length <= 72,
                "Password must not exceed 72 UTF-8 bytes");
        input.setPassword(passwordEncoder.encode(input.getPassword()));
        var created = service.create(input, authentication);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<ResGetUserDTO> getById(@PathVariable long id, Authentication authentication) {
        return ResponseEntity.ok(service.getById(id, authentication));
    }

    @GetMapping("/users")
    public ResponseEntity<ResPageDTO<ResGetUserDTO>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam MultiValueMap<String, String> query, Authentication authentication) {
        var pageable = CrudPageRequest.of(page, size,
                query.getOrDefault("sort", List.of("id,asc")).toArray(String[]::new), SORT_FIELDS);
        return ResponseEntity.ok(service.getAll(pageable, authentication));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<ResUpdateUserDTO> update(@PathVariable long id,
            @RequestBody User input, Authentication authentication) {
        input.setId(id);
        return ResponseEntity.ok(service.update(input, authentication));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable long id, Authentication authentication) {
        service.deleteById(id, authentication);
        return ResponseEntity.noContent().build();
    }
}
