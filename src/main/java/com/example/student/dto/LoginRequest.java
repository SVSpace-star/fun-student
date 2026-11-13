package com.example.student.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {
    @JsonProperty("email")
    @Email (message = "Некорректный email")
    @NotBlank (message = "Email обязателен")
    @Size(max = 254, message = "Электронная почта должна содержать до 254 символов")
    private String email;
    @JsonProperty("password")
    @NotBlank (message = "Пароль обязателен")
    @Size(max = 72, message = "Пароль должен содержать до 72 символов")
    private String password;
}
