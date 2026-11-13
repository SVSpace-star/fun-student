package com.example.student.service;

import com.example.student.dto.user.UserRequest;
import com.example.student.dto.user.UserResponse;
import com.example.student.dto.user.UserResponseList;

public interface UserService {
    UserResponseList findAll();
    UserResponse save(UserRequest userRequest);
    UserResponse updateUser(Long id, UserRequest userRequest);
    void deleteById(Long id);
}
