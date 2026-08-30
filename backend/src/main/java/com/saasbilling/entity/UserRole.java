package com.saasbilling.entity;

/**
 * Roles are intentionally coarse-grained for the MVP. Fine-grained
 * permission checks (e.g. "can manage settings") are implemented in
 * the service layer via {@code hasAnyRole(...)} / a future Permission
 * table, so this enum can be extended without breaking callers.
 */
public enum UserRole {
    OWNER,
    ADMIN,
    MANAGER,
    STAFF
}
