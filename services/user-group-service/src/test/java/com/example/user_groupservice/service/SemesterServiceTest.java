package com.example.user_groupservice.service;

import com.example.user_groupservice.dto.request.CreateSemesterRequest;
import com.example.user_groupservice.dto.request.UpdateSemesterRequest;
import com.example.user_groupservice.entity.Semester;
import com.example.user_groupservice.exception.ConflictException;
import com.example.user_groupservice.exception.ResourceNotFoundException;
import com.example.user_groupservice.repository.SemesterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SemesterServiceTest {

    @Mock
    private SemesterRepository semesterRepository;

    @InjectMocks
    private SemesterService semesterService;

    private CreateSemesterRequest createRequest;

    @BeforeEach
    void setUp() {
        createRequest = CreateSemesterRequest.builder()
            .semesterCode("2026A")
            .semesterName("Semester A")
            .startDate(LocalDate.of(2026, 1, 1))
            .endDate(LocalDate.of(2026, 5, 1))
            .build();
    }

    @Test
    void createSemesterRejectsEndDateBeforeStartDate() {
        CreateSemesterRequest invalidRequest = CreateSemesterRequest.builder()
            .semesterCode("2026A")
            .semesterName("Semester A")
            .startDate(LocalDate.of(2026, 5, 1))
            .endDate(LocalDate.of(2026, 1, 1))
            .build();

        assertThatThrownBy(() -> semesterService.createSemester(invalidRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("End date must be after start date");
    }

    @Test
    void createSemesterRejectsDuplicateCodes() {
        when(semesterRepository.existsBySemesterCode("2026A")).thenReturn(true);

        assertThatThrownBy(() -> semesterService.createSemester(createRequest))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Semester code already exists");
    }

    @Test
    void updateSemesterRejectsInvalidDateRange() {
        Semester semester = semester(1L, "2026A", true);
        when(semesterRepository.findById(1L)).thenReturn(Optional.of(semester));

        UpdateSemesterRequest request = UpdateSemesterRequest.builder()
            .startDate(LocalDate.of(2026, 6, 1))
            .endDate(LocalDate.of(2026, 5, 1))
            .build();

        assertThatThrownBy(() -> semesterService.updateSemester(1L, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("End date must be after start date");
    }

    @Test
    void getSemesterByIdThrowsWhenMissing() {
        when(semesterRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> semesterService.getSemesterById(99L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Semester not found: 99");
    }

    @Test
    void activateSemesterDeactivatesExistingActiveSemesters() {
        Semester target = semester(2L, "2026B", false);
        Semester active = semester(1L, "2026A", true);
        when(semesterRepository.findById(2L)).thenReturn(Optional.of(target));
        when(semesterRepository.findAllActive()).thenReturn(List.of(active));
        when(semesterRepository.save(any(Semester.class))).thenAnswer(invocation -> invocation.getArgument(0));

        semesterService.activateSemester(2L);

        ArgumentCaptor<List<Semester>> activeCaptor = ArgumentCaptor.forClass(List.class);
        verify(semesterRepository).saveAll(activeCaptor.capture());
        verify(semesterRepository).save(target);

        assertThat(activeCaptor.getValue()).singleElement().extracting(Semester::getIsActive).isEqualTo(false);
        assertThat(target.getIsActive()).isTrue();
    }

    private Semester semester(Long id, String code, boolean active) {
        Semester semester = new Semester();
        semester.setId(id);
        semester.setSemesterCode(code);
        semester.setSemesterName("Semester " + code);
        semester.setStartDate(LocalDate.of(2026, 1, 1));
        semester.setEndDate(LocalDate.of(2026, 5, 1));
        semester.setIsActive(active);
        semester.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        semester.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return semester;
    }
}