package com.saasbilling.repository;

import com.saasbilling.entity.InvoiceItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, UUID> {

    interface ProductSalesRow {
        UUID getProductId();
        String getItemName();
        BigDecimal getTotalQuantity();
        BigDecimal getTotalAmount();
    }

    /**
     * Top-selling products by revenue, counted only from non-draft,
     * non-cancelled invoices (a DRAFT hasn't been billed yet, a
     * CANCELLED one was reversed) within the given date range.
     */
    @Query("""
            select i.productId as productId, i.itemName as itemName,
                   sum(i.quantity) as totalQuantity, sum(i.lineTotal) as totalAmount
            from InvoiceItem i
            where i.invoice.businessId = :businessId
              and i.itemType = com.saasbilling.entity.InvoiceItemType.PRODUCT
              and i.invoice.status <> com.saasbilling.entity.InvoiceStatus.DRAFT
              and i.invoice.status <> com.saasbilling.entity.InvoiceStatus.CANCELLED
              and i.invoice.invoiceDate >= :fromDate and i.invoice.invoiceDate <= :toDate
            group by i.productId, i.itemName
            order by sum(i.lineTotal) desc
            """)
    List<ProductSalesRow> topSellingProducts(@Param("businessId") UUID businessId,
                                              @Param("fromDate") LocalDate fromDate,
                                              @Param("toDate") LocalDate toDate,
                                              Pageable pageable);
}
