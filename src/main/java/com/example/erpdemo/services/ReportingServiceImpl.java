package com.example.erpdemo.services;

import com.example.erpdemo.dao.RequestDAO;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;

public class ReportingServiceImpl implements ReportingService {
    @Override
    public Path approvedRequestsPdf(LocalDate from, LocalDate to) throws Exception {
        var rows = RequestDAO.getApprovedRequestsBetween(from, to);
        // burada PDF yazıcı util’inizi çağırın (ör: PdfBoxWriter.write(rows, path))
        Path out = Files.createTempFile("approved-", ".pdf");
        // PdfWriter.write(rows, out);
        return out;
    }
    @Override
    public Path approvedRequestsExcel(LocalDate from, LocalDate to) throws Exception {
        var rows = RequestDAO.getApprovedRequestsBetween(from, to);
        Path out = Files.createTempFile("approved-", ".xlsx");
        // ExcelWriter.write(rows, out);
        return out;
    }
}
