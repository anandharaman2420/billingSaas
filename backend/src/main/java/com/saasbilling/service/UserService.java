package com.saasbilling.service;

import com.saasbilling.entity.User;
import com.saasbilling.exception.ResourceNotFoundException;
import com.saasbilling.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCurrentUserSummary(UUID userId, UUID businessId) {
        User user = userRepository.findWithBusinessByIdAndBusinessId(userId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return Map.of(
                "userId", user.getId(),
                "businessId", user.getBusiness().getId(),
                "businessName", user.getBusiness().getBusinessName(),
                "fullName", user.getFullName(),
                "email", user.getEmail(),
                "role", user.getRole().name()
        );
    }
}
