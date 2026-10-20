package com.ht.robotvacuum.controller;

import com.ht.robotvacuum.dto.request.ReqLoginDTO;
import com.ht.robotvacuum.dto.request.ReqRefreshTokenDTO;
import com.ht.robotvacuum.dto.response.ResAccountDTO;
import com.ht.robotvacuum.dto.response.ResRegisterDTO;
import com.ht.robotvacuum.dto.response.ResTokenDTO;
import com.ht.robotvacuum.mapper.AuthMapper;
import com.ht.robotvacuum.model.User;
import com.ht.robotvacuum.security.AuthenticatedUser;
import com.ht.robotvacuum.security.CurrentUser;
import com.ht.robotvacuum.security.RefreshTokenService;
import com.ht.robotvacuum.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final CurrentUser currentUser;
    private final AuthMapper mapper;

    @PostMapping("/auth/register")
    public ResponseEntity<ResRegisterDTO> register(@Valid @RequestBody User user) {
        Assert.isTrue(user.getId() == 0, "A new user must have id 0");
        validatePasswordLength(user.getPassword());
        String hashPassword = this.passwordEncoder.encode(user.getPassword());
        user.setPassword(hashPassword);
        User created = this.userService.create(user);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(mapper.toRegister(created));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ResTokenDTO> login(@Valid @RequestBody ReqLoginDTO request) {
        validatePasswordLength(request.password());
        Authentication authenticated = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        long id = ((AuthenticatedUser) authenticated.getPrincipal()).getUserId();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(refreshTokenService.login(id));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<ResTokenDTO> refresh(@Valid @RequestBody ReqRefreshTokenDTO request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(refreshTokenService.refresh(request.refreshToken()));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        refreshTokenService.logout(currentUser.requireId(authentication));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/auth/account")
    public ResponseEntity<ResAccountDTO> account(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(mapper.toAccount(userService.getById(currentUser.requireId(authentication))));
    }

    private void validatePasswordLength(String password) {
        Assert.isTrue(password.getBytes(StandardCharsets.UTF_8).length <= 72,
                "Password must not exceed 72 UTF-8 bytes");
    }
}
