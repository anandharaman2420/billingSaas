import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { UserApiService } from '../../../core/services/user-api.service';
import { TeamUser } from '../../../core/models/user.model';
import { PageResponse } from '../../../core/models/common.model';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './user-list.component.html',
})
export class UserListComponent implements OnInit {
  readonly result = signal<PageResponse<TeamUser> | null>(null);
  readonly loading = signal(false);
  page = 0;

  constructor(
    private userApi: UserApiService,
    readonly authService: AuthService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.userApi.list(this.page).subscribe({
      next: (res) => {
        this.result.set(res);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  nextPage(): void {
    if (!this.result()?.last) {
      this.page++;
      this.load();
    }
  }

  prevPage(): void {
    if (!this.result()?.first) {
      this.page--;
      this.load();
    }
  }

  isSelf(user: TeamUser): boolean {
    return this.authService.currentUser()?.id === user.id;
  }

  disable(user: TeamUser): void {
    if (!confirm(`Disable ${user.fullName}'s account?`)) return;
    this.userApi.disable(user.id).subscribe({
      next: () => this.load(),
      error: (err) => alert(err.error?.message ?? 'Failed to disable user'),
    });
  }

  enable(user: TeamUser): void {
    this.userApi.enable(user.id).subscribe(() => this.load());
  }

  roleBadgeClass(role: string): string {
    switch (role) {
      case 'OWNER': return 'badge-danger';
      case 'ADMIN': return 'badge-warning';
      case 'MANAGER': return 'badge-info';
      default: return 'badge-neutral';
    }
  }
}
