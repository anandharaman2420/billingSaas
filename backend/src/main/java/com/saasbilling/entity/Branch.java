package com.saasbilling.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "branches")
public class Branch extends BaseEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "branch_name", nullable = false, length = 150)
    private String branchName;

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
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String gstin;

    @Column(name = "is_default", nullable = false)
    private boolean defaultBranch;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
}
