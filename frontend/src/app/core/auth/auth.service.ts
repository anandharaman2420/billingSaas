import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, AuthUser, LoginRequest, RegisterBusinessRequest } from '../models/auth.model';

const ACCESS_TOKEN_KEY = 'billing_access_token';
const REFRESH_TOKEN_KEY = 'billing_refresh_token';
const USER_KEY = 'billing_user';

/**
 * NOTE on storage: tokens are kept in localStorage for MVP simplicity.
 * For a hardened production deployment, prefer an httpOnly, Secure,
 * SameSite=strict cookie issued by the backend so the access/refresh
 * tokens are never reachable from JS (removes the XSS-token-theft risk).
 * That requires the backend to set the cookie on /login and /refresh
 * and the frontend to stop sending Authorization headers manually.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly apiUrl = `${environment.apiBaseUrl}/auth`;

  // Reactive current-user state the whole app can read (nav bar, guards, role checks).
  readonly currentUser = signal<AuthUser | null>(this.readStoredUser());

  constructor(
    private http: HttpClient,
    private router: Router,
  ) {}

  register(request: RegisterBusinessRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/register`, request)
      .pipe(tap((res) => this.persistSession(res)));
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/login`, request)
      .pipe(tap((res) => this.persistSession(res)));
  }

  refresh(): Observable<AuthResponse> {
    const refreshToken = this.getRefreshToken();
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/refresh`, { refreshToken })
      .pipe(tap((res) => this.persistSession(res)));
  }

  logout(): void {
    const refreshToken = this.getRefreshToken();
    // Best-effort server-side revoke; proceed with local cleanup regardless of the result.
    this.http.post(`${this.apiUrl}/logout`, { refreshToken }).subscribe({
      complete: () => this.clearSessionAndRedirect(),
      error: () => this.clearSessionAndRedirect(),
    });
  }

  private clearSessionAndRedirect(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.currentUser.set(null);
    this.router.navigate(['/login']);
  }

  getAccessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  isAuthenticated(): boolean {
    return !!this.getAccessToken();
  }

  hasRole(...roles: AuthUser['role'][]): boolean {
    const user = this.currentUser();
    return !!user && roles.includes(user.role);
  }

  private persistSession(res: AuthResponse): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, res.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, res.refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify(res.user));
    this.currentUser.set(res.user);
  }

  private readStoredUser(): AuthUser | null {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as AuthUser) : null;
  }
}
