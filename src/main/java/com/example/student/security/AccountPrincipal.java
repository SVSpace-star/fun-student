package com.example.student.security;

import com.example.student.model.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class AccountPrincipal extends org.springframework.security.core.userdetails.User {
    private final Long accountId;

    public AccountPrincipal(User user) {
        super(user.getEmail(), user.getPassword(), user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList());
        accountId = user.getId();
    }

    public Long getAccountId() {
        return accountId;
    }
}
