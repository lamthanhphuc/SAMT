package com.example.user_groupservice.repository;

import com.example.user_groupservice.entity.Semester;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SemesterRepository extends JpaRepository<Semester, Long> {
    
    Optional<Semester> findBySemesterCode(String semesterCode);
    
    Optional<Semester> findByIsActiveTrue();
    
    boolean existsBySemesterCode(String semesterCode);
    
    @Query("SELECT s FROM Semester s WHERE s.isActive = true")
    List<Semester> findAllActive();
    
    @Query("SELECT s FROM Semester s ORDER BY s.startDate DESC")
    List<Semester> findAllOrderByStartDateDesc();
}
