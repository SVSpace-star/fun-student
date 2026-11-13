package com.example.student.repository;

import com.example.student.model.Student;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    @Query("""
            select s from Student s
            where s.id = :id
               or lower(s.name) like :pattern escape '!'
               or lower(s.surname) like :pattern escape '!'
               or lower(concat(concat(s.name, ' '), s.surname)) like :pattern escape '!'
               or lower(concat(concat(s.surname, ' '), s.name)) like :pattern escape '!'
            """)
    Page<Student> search(@Param("pattern") String pattern, @Param("id") Long id, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.id = :id")
    Optional<Student> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("delete from Student s where s.id = :id")
    int deleteStudentById(@Param("id") Long id);
}
