package com.example.reportservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.reportservice.web.UpstreamServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RawSrsBuilder {

    private final ObjectMapper objectMapper;

    public String build(List<EvidenceBlock> evidenceBlocks) {
        try {
            return objectMapper.writeValueAsString(evidenceBlocks);
        } catch (JsonProcessingException ex) {
            throw new UpstreamServiceException("Failed to serialize evidence blocks", ex);
        }
    }
}