package com.example.student.service;

import com.example.student.dto.*;

public interface StudentService {
    StudentResponse save(StudentRequest request);
    StudentFullResponse findById(Long id);
    default StudentResponseList findAll() {
        return findAll(null, "id", "asc", null, null);
    }
    StudentResponseList findAll(String query, String sort, String direction, Integer page, Integer size);
    StudentResponse update(Long id, StudentUpdateRequest request);
    void deleteById(Long id);
}
