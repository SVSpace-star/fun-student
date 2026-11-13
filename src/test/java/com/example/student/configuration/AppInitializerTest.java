package com.example.student.configuration;

import com.example.student.model.Role;
import com.example.student.model.User;
import com.example.student.repository.StudentRepository;
import com.example.student.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AppInitializerTest {
    private final UserRepository users = mock(UserRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final AppInitializer initializer = new AppInitializer(users, encoder, students, new MockEnvironment());

    @Test
    void leavesRepositoriesUntouchedWithoutBootstrapConfiguration() {
        run("", "");
        verifyNoInteractions(users, students);
    }

    @Test
    void createsBootstrapOnceWithHashedPassword() {
        String email = "operator@example.com";
        String password = "bootstrap-password-123";
        when(users.existsByEmail(email)).thenReturn(false, true);

        run(email, password);
        run(email, password);

        var saved = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(email);
        assertThat(saved.getValue().getRoles()).containsExactly(Role.SUPER_ADMIN);
        assertThat(saved.getValue().getPassword()).isNotEqualTo(password);
        assertThat(encoder.matches(password, saved.getValue().getPassword())).isTrue();
        verifyNoInteractions(students);
    }

    @ParameterizedTest
    @CsvSource({"'', valid-password-123", "bad-address, valid-password-123", "operator@example.com, ''",
            "operator@example.com, short"})
    void rejectsInvalidBootstrapConfiguration(String email, String password) {
        assertThatThrownBy(() -> run(email, password)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(users, students);
    }

    private void run(String email, String password) {
        ReflectionTestUtils.setField(initializer, "bootstrapEmail", email);
        ReflectionTestUtils.setField(initializer, "bootstrapPassword", password);
        initializer.run(new DefaultApplicationArguments(new String[0]));
    }
}
