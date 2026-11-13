package com.example.student.controller;

import com.example.student.dto.AuthResponse;
import com.example.student.dto.LoginRequest;
import com.example.student.model.Role;
import com.example.student.security.AccountPrincipal;
import com.example.student.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    @PostMapping("/login")
    public AuthResponse login(@RequestBody @Valid LoginRequest request) {
        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException("Неверная электронная почта или пароль");
        }
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        AccountPrincipal userDetails = (AccountPrincipal) authentication.getPrincipal();

        List<String> roles = userDetails.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();

        String token = jwtService.generateToken(
                userDetails.getUsername(), userDetails.getAccountId(),
                userDetails.getAuthorities().stream()
                        .map(a -> Role.valueOf(a.getAuthority().replace("ROLE_", "")))
                        .collect(Collectors.toSet()));
        return new AuthResponse(token, "Bearer", JwtService.EXPIRATION_SECONDS, roles);
    }
}
