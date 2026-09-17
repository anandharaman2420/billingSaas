package com.saasbilling.controller;

import com.saasbilling.dto.report.ReportDtos.*;
import com.saasbilling.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Reports surface business-wide financial data, so the whole controller
 * is restricted to OWNER/ADMIN/MANAGER, same policy as Expenses.
 */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/sales")
    public SalesReport sales(@RequestParam(required = false) LocalDate fromDate,
                              @RequestParam(required = false) LocalDate toDate) {
        return reportService.salesReport(resolveFrom(fromDate), resolveTo(toDate));
    }

    /** Spec section 19: reports exportable as CSV. Sales is implemented as the representative case. */
    @GetMapping("/sales/export.csv")
    public ResponseEntity<byte[]> exportSalesCsv(@RequestParam(required = false) LocalDate fromDate,
                                                  @RequestParam(required = false) LocalDate toDate) {
        SalesReport report = reportService.salesReport(resolveFrom(fromDate), resolveTo(toDate));

        StringBuilder csv = new StringBuilder("Date,Invoices,Total Sales\n");
        for (DailySalesPoint point : report.dailyBreakdown()) {
            csv.append(point.date()).append(',').append(point.invoiceCount()).append(',').append(point.total()).append('\n');
        }
        csv.append("TOTAL,").append(report.totalInvoices()).append(',').append(report.totalSales()).append('\n');

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sales-report.csv\"")
                .body(bytes);
    }

    @GetMapping("/invoices")
    public InvoiceReport invoices() {
        return reportService.invoiceReport();
    }

    @GetMapping("/payments")
    public PaymentReport payments(@RequestParam(required = false) LocalDate fromDate,
                                   @RequestParam(required = false) LocalDate toDate) {
        return reportService.paymentReport(resolveFrom(fromDate), resolveTo(toDate));
    }

    @GetMapping("/customers")
    public CustomerReport customers(@RequestParam(required = false) LocalDate fromDate,
                                     @RequestParam(required = false) LocalDate toDate,
                                     @RequestParam(defaultValue = "10") int limit) {
        return reportService.customerReport(resolveFrom(fromDate), resolveTo(toDate), Math.min(Math.max(limit, 1), 50));
    }

    @GetMapping("/products")
    public ProductReport products(@RequestParam(required = false) LocalDate fromDate,
                                   @RequestParam(required = false) LocalDate toDate,
                                   @RequestParam(defaultValue = "10") int limit) {
        return reportService.productReport(resolveFrom(fromDate), resolveTo(toDate), Math.min(Math.max(limit, 1), 50));
    }

    @GetMapping("/expenses")
    public ExpenseReport expenses(@RequestParam(required = false) LocalDate fromDate,
                                   @RequestParam(required = false) LocalDate toDate) {
        return reportService.expenseReport(resolveFrom(fromDate), resolveTo(toDate));
    }

    // Default range: current month, if the caller doesn't specify one.
    private LocalDate resolveFrom(LocalDate fromDate) {
        return fromDate != null ? fromDate : LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
    }

    private LocalDate resolveTo(LocalDate toDate) {
        return toDate != null ? toDate : LocalDate.now();
    }
}
