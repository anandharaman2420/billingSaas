package com.saasbilling.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class ReportDtos {

    public record DailySalesPoint(LocalDate date, BigDecimal total, long invoiceCount) {
    }

    public record SalesReport(LocalDate fromDate, LocalDate toDate, BigDecimal totalSales,
                               long totalInvoices, List<DailySalesPoint> dailyBreakdown) {
    }

    public record InvoiceStatusCount(String status, long count) {
    }

    public record InvoiceReport(List<InvoiceStatusCount> statusCounts) {
    }

    public record PaymentMethodTotal(String method, BigDecimal total, long count) {
    }

    public record PaymentReport(LocalDate fromDate, LocalDate toDate, BigDecimal totalCollected,
                                 List<PaymentMethodTotal> byMethod) {
    }

    public record TopCustomer(UUID customerId, String customerName, BigDecimal total, long invoiceCount) {
    }

    public record CustomerReport(LocalDate fromDate, LocalDate toDate, List<TopCustomer> topCustomers,
                                  BigDecimal totalOutstanding) {
    }

    public record TopProduct(UUID productId, String productName, BigDecimal totalQuantity, BigDecimal totalAmount) {
    }

    public record ProductReport(LocalDate fromDate, LocalDate toDate, List<TopProduct> topProducts) {
    }

    public record ExpenseCategoryTotal(UUID categoryId, String categoryName, BigDecimal total) {
    }

    public record ExpenseReport(LocalDate fromDate, LocalDate toDate, BigDecimal totalExpenses,
                                 List<ExpenseCategoryTotal> byCategory) {
    }
}
