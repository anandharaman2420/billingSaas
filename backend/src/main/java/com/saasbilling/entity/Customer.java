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
@Table(name = "customers")
public class Customer extends BaseEntity {

    @Id
    @GeneratedValue
    private UUID id;

    // Plain UUID column rather than @ManyToOne Business: every list/search
    // endpoint filters and sorts by this column, and we never need the
    // full Business entity when reading a customer, so this avoids an
    // unnecessary join (and the lazy-loading trap fixed in UserService).
    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    @Column(length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 20)
    private String pincode;

    @Column(length = 20)
    private String gstin;

    @Column(columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActiveStatus status = ActiveStatus.ACTIVE;
}
