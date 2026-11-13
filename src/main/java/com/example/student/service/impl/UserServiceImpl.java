package com.example.student.service.impl;

import com.example.student.dto.user.UserRequest;
import com.example.student.dto.user.UserResponse;
import com.example.student.dto.user.UserResponseList;
import com.example.student.exception.EmailAlreadyExistsException;
import com.example.student.exception.EntityNotFoundException;
import com.example.student.mapper.UserMapper;
import com.example.student.model.User;
import com.example.student.repository.UserRepository;
import com.example.student.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    @Override
    public UserResponseList findAll() {
        return userMapper.toListResponse(userRepository.findAll(Sort.by("id")));
    }

    @Transactional
    @Override
    public UserResponse save(UserRequest userRequest) {
        checkEmail(userRequest.getEmail(), null);
        User user = userMapper.toEntity(userRequest);
        user.setPassword(encodePassword(userRequest.getPassword()));
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    @Override
    public UserResponse updateUser(Long id, UserRequest request) {
        User user = getUser(id);
        checkEmail(request.getEmail(), id);
        user.setEmail(request.getEmail());
        user.setRoles(new HashSet<>(request.getRoles()));
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(encodePassword(request.getPassword()));
        }
        return userMapper.toResponse(user);
    }

    @Transactional
    @Override
    public void deleteById(Long id) {
        userRepository.delete(getUser(id));
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Пользователь не найден"));
    }

    private void checkEmail(String email, Long currentId) {
        userRepository.findByEmail(email)
                .filter(other -> !other.getId().equals(currentId))
                .ifPresent(other -> {
                    throw new EmailAlreadyExistsException("Такая электронная почта уже используется");
                });
    }

    private String encodePassword(String password) {
        if (password == null || password.isBlank() || password.length() < 6
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Пароль должен содержать не менее 6 символов и не более 72 байт UTF-8");
        }
        return passwordEncoder.encode(password);
    }
}
