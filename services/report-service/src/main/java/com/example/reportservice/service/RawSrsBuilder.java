package com.example.reportservice.service;

import com.example.reportservice.grpc.IssueResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RawSrsBuilder {

    public String build(List<IssueResponse> issues) {

        StringBuilder sb = new StringBuilder();

        sb.append("Software Requirements Specification\n\n");

        int index = 1;

        for (IssueResponse issue : issues) {

            sb.append("FR-")
                    .append(String.format("%03d", index++))
                    .append(": The system SHALL ")
                    .append(issue.getDescription())
                    .append("\n\n");
        }

        return sb.toString();
    }
}