import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../auth/auth.service';

/**
 * Attaches the access token to every request that targets our own API.
 * The backend derives the tenant (business) purely from this token's
 * claims, so the frontend never needs to (and must never) send a
 * business/tenant id explicitly on any request.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getAccessToken();

  const isAuthEndpoint = req.url.includes('/auth/login') || req.url.includes('/auth/register');

  if (token && !isAuthEndpoint) {
    const headers: Record<string, string> = { Authorization: `Bearer ${token}` };
    const selectedBranchId = localStorage.getItem('active-branch-id');
    if (selectedBranchId && !req.url.includes('/branches')) {
      headers['X-Branch-Id'] = selectedBranchId;
    }
    req = req.clone({
      setHeaders: headers,
    });
  }

  return next(req);
};
