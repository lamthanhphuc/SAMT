package com.example.reportservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paged response payload")
public class PageResponse<T> {

    @Schema(description = "Current page content")
    private List<T> content;

    @Schema(example = "0")
    private int page;

    @Schema(example = "20")
    private int size;

    @Schema(example = "42")
    private long totalElements;

    @Schema(example = "3")
    private int totalPages;
}