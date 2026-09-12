import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit {
  readonly me = signal<Record<string, unknown> | null>(null);

  constructor(
    private http: HttpClient,
    readonly authService: AuthService,
  ) {}

  ngOnInit(): void {
    this.http.get<Record<string, unknown>>(`${environment.apiBaseUrl}/me`).subscribe({
      next: (res) => this.me.set(res),
    });
  }
}
