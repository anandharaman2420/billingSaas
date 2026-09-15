package com.saasbilling.dto.dashboard;

import java.math.BigDecimal;

public record DashboardResponse(
        BigDecimal todaySales,
        BigDecimal monthSales,
        BigDecimal totalSales,
        BigDecimal pendingPayments,
        BigDecimal paidAmount,
        long invoiceCount,
        long customerCount,
        long productCount
) {
}
