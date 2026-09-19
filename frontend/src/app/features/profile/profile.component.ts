import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { UserApiService } from '../../core/services/user-api.service';
import { ApiError } from '../../core/models/auth.model';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './profile.component.html',
})
export class ProfileComponent {
  readonly profileLoading = signal(false);
  readonly profileSaved = signal(false);
  readonly profileError = signal<string | null>(null);

  readonly passwordLoading = signal(false);
  readonly passwordSaved = signal(false);
  readonly passwordError = signal<string | null>(null);

  readonly profileForm = this.fb.group({
    fullName: [this.authService.currentUser()?.fullName ?? '', Validators.required],
    phone: [''],
  });

  readonly passwordForm = this.fb.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
  });

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private userApi: UserApiService,
    readonly authService: AuthService,
  ) {}

  saveProfile(): void {
    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      return;
    }
    this.profileLoading.set(true);
    this.profileError.set(null);
    this.profileSaved.set(false);

    const raw = this.profileForm.getRawValue();
    this.userApi.updateOwnProfile({ fullName: raw.fullName!, phone: raw.phone || undefined }).subscribe({
      next: () => {
        this.profileLoading.set(false);
        this.profileSaved.set(true);
      },
      error: (err: HttpErrorResponse) => {
        const apiError = err.error as ApiError | undefined;
        this.profileError.set(apiError?.message ?? 'Failed to update profile.');
        this.profileLoading.set(false);
      },
    });
  }

  changePassword(): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    this.passwordLoading.set(true);
    this.passwordError.set(null);
    this.passwordSaved.set(false);

    this.http.post(`${environment.apiBaseUrl}/auth/change-password`, this.passwordForm.getRawValue()).subscribe({
      next: () => {
        this.passwordLoading.set(false);
        this.passwordSaved.set(true);
        this.passwordForm.reset();
      },
      error: (err: HttpErrorResponse) => {
        const apiError = err.error as ApiError | undefined;
        this.passwordError.set(apiError?.message ?? 'Failed to change password.');
        this.passwordLoading.set(false);
      },
    });
  }
}
