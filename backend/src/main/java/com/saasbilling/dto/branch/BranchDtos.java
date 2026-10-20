package com.saasbilling.dto.branch;

import com.saasbilling.entity.Branch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class BranchDtos {

    public record BranchRequest(
            @NotBlank @Size(max = 150) String branchName,
            @Size(max = 255) String addressLine,
            @Size(max = 100) String city,
            @Size(max = 100) String state,
            @Size(max = 20) String pincode,
            @Size(max = 100) String country,
            @Size(max = 20) String phone,
            @Size(max = 150) String email,
            @Size(max = 20) String gstin
    ) {
    }

    public record BranchResponse(
            UUID id, String branchName, String addressLine, String city, String state,
            String pincode, String country, String phone, String email, String gstin,
            boolean defaultBranch, String status
    ) {
        public static BranchResponse from(Branch branch) {
            return new BranchResponse(branch.getId(), branch.getBranchName(), branch.getAddressLine(),
                    branch.getCity(), branch.getState(), branch.getPincode(), branch.getCountry(),
                    branch.getPhone(), branch.getEmail(), branch.getGstin(), branch.isDefaultBranch(),
                    branch.getStatus());
        }
    }
}
