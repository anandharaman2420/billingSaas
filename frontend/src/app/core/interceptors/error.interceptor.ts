import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';

/**
 * Centralized error handling so components don't each need to know
 * about the backend's ApiError shape or auth-expiry behavior.
 *
 * NOTE: this does a hard logout on 401 rather than a silent token
 * refresh-and-retry, to keep Phase 1 simple and predictable. Swap in
 * a refresh-and-retry flow (using AuthService.refresh()) once the
 * rest of the API surface exists and this needs to feel seamless.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      const isAuthEndpoint = req.url.includes('/auth/login') || req.url.includes('/auth/register');

      if (error.status === 401 && !isAuthEndpoint) {
        authService.logout();
      } else if (error.status === 403) {
        router.navigate(['/forbidden']);
      }

      return throwError(() => error);
    }),
  );
};
