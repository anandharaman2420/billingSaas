package com.saasbilling.service;

import com.saasbilling.entity.User;
import com.saasbilling.exception.ResourceNotFoundException;
import com.saasbilling.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Reads the current user together with its Business, inside a
     * read-only transaction, so lazy fields (user.getBusiness().*) can
     * be safely accessed by the caller after this method returns -
     * as long as the caller reads them from the returned entity
     * immediately rather than storing it and reading later.
     */
    @Transactional(readOnly = true)
    public User getCurrentUserWithBusiness(UUID userId, UUID businessId) {
        return userRepository.findWithBusinessByIdAndBusinessId(userId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
