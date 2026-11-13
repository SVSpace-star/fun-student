package com.example.student.security;

import com.example.student.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

@Service
public class JwtService {
    public static final int EXPIRATION_SECONDS = 3600;

    private final SecretKey secretKey;
    private final JwtParser parser;

    public JwtService(@Value("${app.jwt.secret:}") String secret) {
        secretKey = secret.isBlank() ? Jwts.SIG.HS256.key().build()
                : Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        parser = Jwts.parser().verifyWith(secretKey).build();
    }

    public String generateToken(String username, Long accountId, Set<Role> roles) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("accountId", accountId.toString())
                .claim("roles", roles.stream().map(Enum::name).sorted().toList())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(EXPIRATION_SECONDS)))
                .signWith(secretKey)
                .compact();
    }

    SessionClaims parseToken(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        return new SessionClaims(claims.getSubject(), claims.get("accountId", String.class));
    }

    record SessionClaims(String username, String accountId) {}
}
