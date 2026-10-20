import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-forbidden',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="page page-sm" style="text-align: center; padding-top: 60px;">
      <div class="card" style="padding: 48px 24px;">
        <div style="font-size: 54px; margin-bottom: 16px;" aria-hidden="true">🚫</div>
        <h1 style="margin: 0 0 8px; font-size: 26px;">403 — Access Denied</h1>
        <p class="text-muted" style="max-width: 380px; margin: 0 auto 24px; font-size: 14px;">
          You don't have permission to access this page or perform this action. Contact your business administrator if you need access.
        </p>
        <a routerLink="/dashboard" class="btn btn-primary" aria-label="Return to dashboard">Return to Dashboard</a>
      </div>
    </div>
  `,
})
export class ForbiddenComponent {}
