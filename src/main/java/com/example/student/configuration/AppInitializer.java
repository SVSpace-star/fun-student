package com.example.student.configuration;

import com.example.student.model.Role;
import com.example.student.model.Student;
import com.example.student.model.User;
import com.example.student.repository.StudentRepository;
import com.example.student.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AppInitializer implements ApplicationRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StudentRepository studentRepository;
    private final Environment environment;

    @Value("${app.bootstrap.email:}")
    private String bootstrapEmail;

    @Value("${app.bootstrap.password:}")
    private String bootstrapPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (environment.acceptsProfiles(Profiles.of("demo"))) {
            createUserIfNotExists("superadmin@example.com", "superAdmin", Set.of(Role.SUPER_ADMIN));
            createUserIfNotExists("admin@example.com", "admin", Set.of(Role.ADMIN));
            createUserIfNotExists("teacher@example.com", "teacher", Set.of(Role.TEACHER));
            createStudentsIfEmpty();
        } else if (!bootstrapEmail.isBlank() || !bootstrapPassword.isBlank()) {
            createBootstrapUser();
        }
    }

    private void createBootstrapUser() {
        if (bootstrapEmail.length() > 254 || !bootstrapEmail.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
                || bootstrapPassword.isBlank() || bootstrapPassword.length() < 12
                || bootstrapPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Для начальной учётной записи укажите корректный email "
                    + "и пароль от 12 символов до 72 байт UTF-8");
        }
        createUserIfNotExists(bootstrapEmail, bootstrapPassword, Set.of(Role.SUPER_ADMIN));
    }

    private void createUserIfNotExists(String email, String rawPassword, Set<Role> roles) {
        if (!userRepository.existsByEmail(email)) {
            User user = new User();
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setRoles(roles);
            userRepository.save(user);
        }
    }
    private void createStudentsIfEmpty() {
        if (studentRepository.count() == 0) {
            studentRepository.saveAll(List.of(
                    demoStudent("Алина", "Громова", "alina@example.com"),
                    demoStudent("Артём", "Данилов", "artem@example.com"),
                    demoStudent("Мария", "Орлова", "maria@example.com"),
                    demoStudent("Никита", "Соколов", "nikita@example.com"),
                    demoStudent("Софья", "Белова", "sofya@example.com")));
        }
    }

    private Student demoStudent(String name, String surname, String email) {
        Student student = new Student();
        student.setName(name);
        student.setSurname(surname);
        student.setEmail(email);
        return student;
    }
}
