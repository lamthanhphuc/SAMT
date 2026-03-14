package com.example.reportservice.service.impl;


import com.example.reportservice.dto.IssueDto;

import java.util.List;

public interface ReportService {

    List<IssueDto> generateReport(Long projectConfigId);
}
