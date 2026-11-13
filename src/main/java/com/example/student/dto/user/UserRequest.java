package com.example.student.dto.user;

import com.example.student.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserRequest {
    @NotBlank(message = "Укажите электронную почту")
    @Email(message = "Проверьте формат электронной почты")
    @Size(max = 254, message = "Электронная почта должна содержать до 254 символов")
    private String email;
    private String password;
    @NotEmpty(message = "Выберите хотя бы одну роль")
    private Set<@NotNull Role> roles;
}
