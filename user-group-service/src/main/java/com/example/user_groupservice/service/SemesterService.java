package com.example.user_groupservice.service;

import com.example.user_groupservice.dto.request.CreateSemesterRequest;
import com.example.user_groupservice.dto.request.UpdateSemesterRequest;
import com.example.user_groupservice.dto.response.SemesterResponse;
import com.example.user_groupservice.entity.Semester;
import com.example.user_groupservice.exception.ConflictException;
import com.example.user_groupservice.exception.ResourceNotFoundException;
import com.example.user_groupservice.repository.SemesterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SemesterService {
    
    private final SemesterRepository semesterRepository;
    
    @Transactional
    public SemesterResponse createSemester(CreateSemesterRequest request) {
        log.info("Creating semester: {}", request.getSemesterCode());
        
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("End date must be after start date");
        }
        
        if (semesterRepository.existsBySemesterCode(request.getSemesterCode())) {
            throw new ConflictException(
                "SEMESTER_CODE_EXISTS",
                "Semester code already exists: " + request.getSemesterCode()
            );
        }
        
        Semester semester = new Semester();
        semester.setSemesterCode(request.getSemesterCode());
        semester.setSemesterName(request.getSemesterName());
        semester.setStartDate(request.getStartDate());
        semester.setEndDate(request.getEndDate());
        semester.setIsActive(false);
        
        semester = semesterRepository.save(semester);
        log.info("Created semester: id={}, code={}", semester.getId(), semester.getSemesterCode());
        
        return toResponse(semester);
    }
    
    @Transactional(readOnly = true)
    public SemesterResponse getSemesterById(Long id) {
        Semester semester = semesterRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "SEMESTER_NOT_FOUND",
                "Semester not found: " + id
            ));
        return toResponse(semester);
    }
    
    @Transactional(readOnly = true)
    public SemesterResponse getSemesterByCode(String code) {
        Semester semester = semesterRepository.findBySemesterCode(code)
            .orElseThrow(() -> new ResourceNotFoundException(
                "SEMESTER_NOT_FOUND",
                "Semester not found: " + code
            ));
        return toResponse(semester);
    }
    
    @Transactional(readOnly = true)
    public SemesterResponse getActiveSemester() {
        Semester semester = semesterRepository.findByIsActiveTrue()
            .orElseThrow(() -> new ResourceNotFoundException(
                "NO_ACTIVE_SEMESTER",
                "No active semester found"
            ));
        return toResponse(semester);
    }
    
    @Transactional(readOnly = true)
    public List<SemesterResponse> listAllSemesters() {
        return semesterRepository.findAllOrderByStartDateDesc().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }
    
    @Transactional
    public SemesterResponse updateSemester(Long id, UpdateSemesterRequest request) {
        Semester semester = semesterRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "SEMESTER_NOT_FOUND",
                "Semester not found: " + id
            ));
        
        if (request.getSemesterName() != null) {
            semester.setSemesterName(request.getSemesterName());
        }
        if (request.getStartDate() != null) {
            semester.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            semester.setEndDate(request.getEndDate());
        }
        
        if (semester.getEndDate().isBefore(semester.getStartDate())) {
            throw new IllegalArgumentException("End date must be after start date");
        }
        
        semester = semesterRepository.save(semester);
        return toResponse(semester);
    }
    
    @Transactional
    public void activateSemester(Long id) {
        Semester semester = semesterRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "SEMESTER_NOT_FOUND",
                "Semester not found: " + id
            ));
        
        List<Semester> activeSemesters = semesterRepository.findAllActive();
        activeSemesters.forEach(s -> s.setIsActive(false));
        semesterRepository.saveAll(activeSemesters);
        
        semester.setIsActive(true);
        semesterRepository.save(semester);
        
        log.info("Activated semester: id={}, code={}", id, semester.getSemesterCode());
    }
    
    private SemesterResponse toResponse(Semester semester) {
        return SemesterResponse.builder()
            .id(semester.getId())
            .semesterCode(semester.getSemesterCode())
            .semesterName(semester.getSemesterName())
            .startDate(semester.getStartDate())
            .endDate(semester.getEndDate())
            .isActive(semester.getIsActive())
            .createdAt(semester.getCreatedAt())
            .updatedAt(semester.getUpdatedAt())
            .build();
    }
}
