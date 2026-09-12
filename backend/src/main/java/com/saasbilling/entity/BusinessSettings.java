package com.saasbilling.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "business_settings")
public class BusinessSettings extends BaseEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false, unique = true)
    private Business business;

    @Column(name = "invoice_prefix", nullable = false, length = 20)
    private String invoicePrefix = "INV";

    @Column(name = "invoice_number_format", nullable = false, length = 50)
    private String invoiceNumberFormat = "{PREFIX}-{YEAR}-{SEQ:00000}";

    @Column(name = "invoice_next_sequence", nullable = false)
    private long invoiceNextSequence = 1L;

    @Column(name = "financial_year_start_month", nullable = false)
    private short financialYearStartMonth = 4;

    @Column(name = "default_due_days", nullable = false)
    private int defaultDueDays = 7;

    @Column(nullable = false, length = 10)
    private String currency = "INR";

    @Column(name = "tax_mode", nullable = false, length = 20)
    private String taxMode = "EXCLUSIVE";

    @Column(name = "invoice_notes", columnDefinition = "text")
    private String invoiceNotes;

    @Column(name = "invoice_terms", columnDefinition = "text")
    private String invoiceTerms;

    @Column(name = "theme_preference", nullable = false, length = 10)
    private String themePreference = "SYSTEM";
}
