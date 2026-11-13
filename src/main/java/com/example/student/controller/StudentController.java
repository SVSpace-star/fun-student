package com.example.student.controller;

import com.example.student.dto.*;
import com.example.student.service.StudentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/student")
public class StudentController {
    private final StudentService studentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public StudentResponseList findAll(
            @RequestParam(name = "query", required = false) @Size(max = 200) String query,
            @RequestParam(name = "sort", defaultValue = "id") String sort,
            @RequestParam(name = "direction", defaultValue = "asc") String direction,
            @RequestParam(name = "page", required = false) @Min(0) Integer page,
            @RequestParam(name = "size", required = false) @Min(1) @Max(100) Integer size) {
        return studentService.findAll(query, sort, direction, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public StudentFullResponse findStudentById(@PathVariable Long id) {
        return studentService.findById(id);
    }

    @GetMapping("info/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public StudentFullResponse getFullInfoById(@PathVariable Long id) {
        return studentService.findById(id);
    }

    @PostMapping("/save")
    @PreAuthorize("hasRole('ADMIN')")
    public StudentResponse createStudent(@Valid @RequestBody StudentRequest studentRequest) {
        return studentService.save(studentRequest);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public StudentResponse updateStudent(@PathVariable Long id, @Valid @RequestBody StudentUpdateRequest request) {
        return studentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteStudentById(@PathVariable Long id) {
        studentService.deleteById(id);
    }
}
