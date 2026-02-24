package com.example.user_groupservice.controller;

import com.example.user_groupservice.dto.request.CreateSemesterRequest;
import com.example.user_groupservice.dto.request.UpdateSemesterRequest;
import com.example.user_groupservice.dto.response.SemesterResponse;
import com.example.user_groupservice.service.SemesterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/semesters")
@RequiredArgsConstructor
@Slf4j
public class SemesterController {
    
    private final SemesterService semesterService;
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SemesterResponse> createSemester(
            @Valid @RequestBody CreateSemesterRequest request) {
        SemesterResponse response = semesterService.createSemester(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SemesterResponse> getSemesterById(@PathVariable Long id) {
        SemesterResponse response = semesterService.getSemesterById(id);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/code/{code}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SemesterResponse> getSemesterByCode(@PathVariable String code) {
        SemesterResponse response = semesterService.getSemesterByCode(code);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SemesterResponse> getActiveSemester() {
        SemesterResponse response = semesterService.getActiveSemester();
        return ResponseEntity.ok(response);
    }
    
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SemesterResponse>> listAllSemesters() {
        List<SemesterResponse> response = semesterService.listAllSemesters();
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SemesterResponse> updateSemester(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSemesterRequest request) {
        SemesterResponse response = semesterService.updateSemester(id, request);
        return ResponseEntity.ok(response);
    }
    
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> activateSemester(@PathVariable Long id) {
        semesterService.activateSemester(id);
        return ResponseEntity.noContent().build();
    }
}
