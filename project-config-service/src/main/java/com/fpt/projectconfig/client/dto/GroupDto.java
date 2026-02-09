package com.fpt.projectconfig.client.dto;

import lombok.Data;

import java.util.UUID;

/**
 * DTO cho User-Group Service
 * TODO: Update khi User-Group Service có API chính thức
 */
@Data
public class GroupDto {
    private UUID id;
    private String name;
    private Long leaderId;
    private String status; // ACTIVE, DELETED
}
