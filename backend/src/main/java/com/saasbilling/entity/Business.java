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
@Table(name = "businesses")
public class Business extends BaseEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_name", nullable = false, length = 200)
    private String businessName;

    @Column(name = "business_type", length = 100)
    private String businessType;

    @Column(name = "owner_name", nullable = false, length = 150)
    private String ownerName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 20)
    private String pincode;

    @Column(length = 100)
    private String country = "India";

    @Column(length = 20)
    private String gstin;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(length = 30, nullable = false)
    private String plan = "FREE";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BusinessStatus status = BusinessStatus.ACTIVE;
}
