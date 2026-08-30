package com.saasbilling.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(length = 50)
    private String sku;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 20)
    private String unit = "PCS";

    @Column(name = "purchase_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal purchasePrice = BigDecimal.ZERO;

    @Column(name = "selling_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal sellingPrice = BigDecimal.ZERO;

    @Column(name = "tax_rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRatePercent = BigDecimal.ZERO;

    @Column(name = "stock_quantity", nullable = false, precision = 14, scale = 2)
    private BigDecimal stockQuantity = BigDecimal.ZERO;

    @Column(name = "minimum_stock_level", nullable = false, precision = 14, scale = 2)
    private BigDecimal minimumStockLevel = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActiveStatus status = ActiveStatus.ACTIVE;
}
