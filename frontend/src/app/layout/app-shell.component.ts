import { CommonModule } from '@angular/common';
import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { BranchApiService } from '../core/services/branch-api.service';
import { Branch } from '../core/models/branch.model';
import { GlobalSearchComponent } from './global-search.component';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet, GlobalSearchComponent],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
})
export class AppShellComponent implements OnInit {
  readonly authService = inject(AuthService);

  sidebarCollapsed = false;
  mobileSidebarOpen = false;
  darkMode = false;
  readonly branches = signal<Branch[]>([]);
  readonly selectedBranchId = signal('');
  readonly branchLoadError = signal<string | null>(null);

  private readonly branchApi = inject(BranchApiService);

  readonly userInitials = computed(() => {
    const name = this.authService.currentUser()?.fullName ?? '';
    return name
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((w: string) => w[0].toUpperCase())
      .join('');
  });

  ngOnInit(): void {
    // Restore sidebar collapsed state
    const collapsed = localStorage.getItem('sidebar-collapsed');
    if (collapsed === 'true') this.sidebarCollapsed = true;

    // Restore dark mode
    const dark = localStorage.getItem('dark-mode');
    if (dark === 'true') {
      this.darkMode = true;
      document.documentElement.setAttribute('data-theme', 'dark');
    }

    // Auto-collapse on small screens
    this.checkMobile();

    this.branchApi.list().subscribe({
      next: (branches) => {
        this.branchLoadError.set(null);
        this.branches.set(branches);
        const selected = branches.find((branch) => branch.id === this.branchApi.selectedId())
          ?? branches.find((branch) => branch.defaultBranch)
          ?? branches[0];
        if (selected) {
          this.selectedBranchId.set(selected.id);
          if (this.branchApi.selectedId() !== selected.id) {
            const hadSelection = this.branchApi.selectedId() !== null;
            this.branchApi.select(selected.id);
            if (hadSelection) window.location.reload();
          }
        }
      },
      error: () => this.branchLoadError.set('Unable to load your branches. Reload the page to retry.'),
    });
  }

  selectBranch(id: string): void {
    if (!id || id === this.selectedBranchId()) return;
    this.branchApi.select(id);
    window.location.reload();
  }

  @HostListener('window:resize')
  checkMobile(): void {
    if (window.innerWidth < 768) {
      this.sidebarCollapsed = true;
    }
  }

  toggleSidebar(): void {
    if (window.innerWidth < 768) {
      this.mobileSidebarOpen = !this.mobileSidebarOpen;
    } else {
      this.sidebarCollapsed = !this.sidebarCollapsed;
      localStorage.setItem('sidebar-collapsed', String(this.sidebarCollapsed));
    }
  }

  closeMobileSidebar(): void {
    this.mobileSidebarOpen = false;
  }

  toggleDarkMode(): void {
    this.darkMode = !this.darkMode;
    document.documentElement.setAttribute('data-theme', this.darkMode ? 'dark' : '');
    localStorage.setItem('dark-mode', String(this.darkMode));
  }

  logout(): void {
    this.authService.logout();
  }
}