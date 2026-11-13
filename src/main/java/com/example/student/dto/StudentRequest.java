package com.example.student.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class StudentRequest {
    @NotBlank(message = "Введите имя")
    @Size(max = 255, message = "Имя должно содержать не более 255 символов")
    private String name;
    @NotBlank(message = "Введите фамилию")
    @Size(max = 255, message = "Фамилия должна содержать не более 255 символов")
    private String surname;
    @JsonProperty("phone_number")
    @JsonAlias("phoneNumber")
    @Size(max = 32, message = "Телефон должен содержать не более 32 символов")
    @Pattern(regexp = "^(?:\\s*|\\+?[0-9(). \\-]*[0-9][0-9(). \\-]*)$",
            message = "Введите номер телефона с цифрами, пробелами и знаками +, -, (, )")
    private String phoneNumber;
    @Email(message = "Введите корректный email")
    @Size(max = 255, message = "Email должен содержать не более 255 символов")
    private String email;
}
