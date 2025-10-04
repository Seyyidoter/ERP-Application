package com.example.erpdemo.services;
import java.nio.file.Path;
import java.time.LocalDate;

public interface ReportingService {
    Path approvedRequestsPdf(LocalDate from, LocalDate to) throws Exception;
    Path approvedRequestsExcel(LocalDate from, LocalDate to) throws Exception;
}
