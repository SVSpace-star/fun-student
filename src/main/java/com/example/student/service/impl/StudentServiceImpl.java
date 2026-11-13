package com.example.student.service.impl;

import com.example.student.dto.*;
import com.example.student.exception.EntityNotFoundException;
import com.example.student.mapper.StudentMapper;
import com.example.student.model.Student;
import com.example.student.repository.StudentRepository;
import com.example.student.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {
    private static final Set<String> SORT_FIELDS = Set.of("id", "name", "surname");
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final StudentRepository studentRepository;
    private final StudentMapper studentMapper;

    @Transactional
    @Override
    public StudentResponse save(StudentRequest studentRequest) {
        Student student = studentMapper.requestToStudent(studentRequest);
        student.setName(student.getName().strip());
        student.setSurname(student.getSurname().strip());
        student.setPhoneNumber(optionalContact(student.getPhoneNumber()));
        student.setEmail(optionalContact(student.getEmail()));
        return studentMapper.studentToResponse(studentRepository.save(student));
    }

    @Transactional(readOnly = true)
    @Override
    public StudentFullResponse findById(Long id) {
        return studentMapper.studentToFullResponse(getStudentById(id));
    }

    @Transactional(readOnly = true)
    @Override
    public StudentResponseList findAll(String query, String sort, String direction, Integer page, Integer size) {
        if (!SORT_FIELDS.contains(sort)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сортировка доступна по id, name и surname");
        }
        Sort.Direction orderDirection = Sort.Direction.fromOptionalString(direction)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите направление asc или desc"));
        Sort.Order order = new Sort.Order(orderDirection, sort);
        Sort ordering = sort.equals("id") ? Sort.by(order) : Sort.by(order.ignoreCase(), Sort.Order.asc("id"));
        Pageable pageable = pageRequest(ordering, page, size);
        String search = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        Page<Student> students = search.isEmpty()
                ? studentRepository.findAll(pageable)
                : studentRepository.search(containsPattern(search), studentId(search), pageable);
        return new StudentResponseList(studentMapper.studentListToListOfStudentResponse(students.getContent()),
                Math.toIntExact(students.getTotalElements()));
    }

    @Transactional
    @Override
    public StudentResponse update(Long id, StudentUpdateRequest request) {
        Student student = studentRepository.findByIdForUpdate(id).orElseThrow(() -> studentNotFound(id));
        if (request.getName() != null) {
            student.setName(request.getName().strip());
        }
        if (request.getSurname() != null) {
            student.setSurname(request.getSurname().strip());
        }
        if (request.getPhoneNumber() != null) {
            student.setPhoneNumber(optionalContact(request.getPhoneNumber()));
        }
        if (request.getEmail() != null) {
            student.setEmail(optionalContact(request.getEmail()));
        }
        return studentMapper.studentToResponse(student);
    }

    @Transactional
    @Override
    public void deleteById(Long id) {
        if (studentRepository.deleteStudentById(id) == 0) {
            throw studentNotFound(id);
        }
    }

    private Pageable pageRequest(Sort sort, Integer page, Integer size) {
        if (page == null && size == null) {
            return Pageable.unpaged(sort);
        }
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if ((long) pageNumber * pageSize > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Слишком большой номер страницы");
        }
        return PageRequest.of(pageNumber, pageSize, sort);
    }

    private String containsPattern(String query) {
        return "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private Long studentId(String query) {
        if (!query.matches("[0-9]+")) {
            return null;
        }
        try {
            return Long.valueOf(query);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String optionalContact(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private Student getStudentById(Long id) {
        return studentRepository.findById(id).orElseThrow(() -> studentNotFound(id));
    }

    private EntityNotFoundException studentNotFound(Long id) {
        return new EntityNotFoundException("Студент с ID " + id + " не найден");
    }
}
