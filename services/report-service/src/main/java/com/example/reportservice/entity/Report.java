package com.example.reportservice.entity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID reportId;

    private String projectConfigId;

    @Enumerated(EnumType.STRING)
    private ReportType type;

    private String filePath;

    private UUID createdBy;

    private LocalDateTime createdAt;
}

