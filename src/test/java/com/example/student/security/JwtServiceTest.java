package com.example.student.security;

import com.example.student.model.Role;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {
    @Test
    void acceptsTokenAcrossInstancesWithSameConfiguredKey() {
        String secret = Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());
        String token = new JwtService(secret).generateToken("teacher@example.com", 7L, Set.of(Role.TEACHER));

        var claims = new JwtService(secret).parseToken(token);
        assertThat(claims.username()).isEqualTo("teacher@example.com");
        assertThat(claims.accountId()).isEqualTo("7");
    }

    @Test
    void rejectsAnExpiredTokenEvenWithValidSignature() {
        var key = Jwts.SIG.HS256.key().build();
        String token = Jwts.builder().subject("teacher@example.com")
                .expiration(Date.from(Instant.now().minusSeconds(60))).signWith(key).compact();
        var jwt = new JwtService(Encoders.BASE64.encode(key.getEncoded()));

        assertThatThrownBy(() -> jwt.parseToken(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsTokensFromUnrelatedProcessKey() {
        String token = new JwtService("").generateToken("teacher@example.com", 7L, Set.of(Role.TEACHER));
        var otherProcess = new JwtService("");

        assertThatThrownBy(() -> otherProcess.parseToken(token)).isInstanceOf(JwtException.class);
    }
}
