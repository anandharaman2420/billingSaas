import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { UserRole } from '../models/auth.model';

/**
 * Frontend role gating is a UX convenience only - it hides routes/buttons
 * the user can't use. It is NOT a security boundary; the backend enforces
 * permissions independently on every request (see spec section 5).
 */
export function roleGuard(...allowedRoles: UserRole[]): CanActivateFn {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (authService.hasRole(...allowedRoles)) {
      return true;
    }

    router.navigate(['/forbidden']);
    return false;
  };
}
