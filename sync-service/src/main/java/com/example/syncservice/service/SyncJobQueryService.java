package com.example.syncservice.service;

import com.example.syncservice.dto.PageResponse;
import com.example.syncservice.dto.SyncJobResponse;
import com.example.syncservice.entity.SyncJob;
import com.example.syncservice.repository.SyncJobRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SyncJobQueryService {

    private final SyncJobRepository syncJobRepository;

    public SyncJobResponse getSyncJob(Long syncJobId) {
        SyncJob syncJob = syncJobRepository.findById(syncJobId)
            .filter(job -> job.getDeletedAt() == null)
            .orElseThrow(() -> new EntityNotFoundException("Sync job not found"));

        return toResponse(syncJob);
    }

    public PageResponse<SyncJobResponse> listSyncJobs(
        UUID projectConfigId,
        SyncJob.JobType jobType,
        SyncJob.JobStatus status,
        int page,
        int size
    ) {
        Specification<SyncJob> specification = (root, query, builder) -> builder.isNull(root.get("deletedAt"));

        if (projectConfigId != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("projectConfigId"), projectConfigId));
        }

        if (jobType != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("jobType"), jobType));
        }

        if (status != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }

        Page<SyncJob> syncJobPage = syncJobRepository.findAll(
            specification,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        return PageResponse.<SyncJobResponse>builder()
            .content(syncJobPage.getContent().stream().map(this::toResponse).toList())
            .page(page)
            .size(size)
            .totalElements(syncJobPage.getTotalElements())
            .totalPages(syncJobPage.getTotalPages())
            .build();
    }

    private SyncJobResponse toResponse(SyncJob syncJob) {
        return SyncJobResponse.builder()
            .syncJobId(syncJob.getId())
            .projectConfigId(syncJob.getProjectConfigId())
            .jobType(syncJob.getJobType().name())
            .status(syncJob.getStatus().name())
            .startedAt(syncJob.getStartedAt() == null ? null : syncJob.getStartedAt().atOffset(ZoneOffset.UTC))
            .completedAt(syncJob.getCompletedAt() == null ? null : syncJob.getCompletedAt().atOffset(ZoneOffset.UTC))
            .recordsFetched(syncJob.getRecordsFetched())
            .recordsSaved(syncJob.getRecordsSaved())
            .errorMessage(syncJob.getErrorMessage())
            .degraded(syncJob.getStatus() == SyncJob.JobStatus.PARTIAL_FAILURE)
            .correlationId(syncJob.getCorrelationId())
            .build();
    }
}