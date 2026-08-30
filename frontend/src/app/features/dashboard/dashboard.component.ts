import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit {
  readonly me = signal<Record<string, unknown> | null>(null);

  constructor(
    private http: HttpClient,
    readonly authService: AuthService,
  ) {}

  ngOnInit(): void {
    // Confirms end-to-end: JWT -> backend TenantContext -> tenant-scoped
    // user lookup. Real dashboard metrics (sales, pending payments, etc.
    // per spec section 7) land here once the invoices/payments modules exist.
    this.http.get<Record<string, unknown>>(`${environment.apiBaseUrl}/me`).subscribe({
      next: (res) => this.me.set(res),
    });
  }

  logout(): void {
    this.authService.logout();
  }
}
