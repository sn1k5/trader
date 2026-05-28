package com.cpptrader.admin.report;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/daily")
    public Map<String, Object> getDailyReport(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reportService.getDailyReport(date);
    }

    @GetMapping("/pnl")
    public Map<String, Object> getPnlReport() {
        return reportService.getPnlReport();
    }

    @GetMapping("/fund-flow")
    public Map<String, Object> getFundFlow() {
        return reportService.getFundFlow();
    }

    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        return reportService.getSummary();
    }
}
