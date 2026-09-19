import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { ApiError } from '../../core/models/auth.model';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './settings.component.html',
})
export class SettingsComponent implements OnInit {
  readonly profileLoading = signal(false);
  readonly profileSaved = signal(false);
  readonly profileError = signal<string | null>(null);

  readonly settingsLoading = signal(false);
  readonly settingsSaved = signal(false);
  readonly settingsError = signal<string | null>(null);

  readonly profileForm = this.fb.group({
    businessName: ['', Validators.required],
    businessType: [''],
    addressLine: [''],
    city: [''],
    state: [''],
    pincode: [''],
    country: [''],
    phone: [''],
    email: ['', Validators.email],
    website: [''],
    logoUrl: [''],
    gstin: [''],
  });

  readonly settingsForm = this.fb.group({
    invoicePrefix: ['INV', Validators.required],
    invoiceNumberFormat: ['{PREFIX}-{YEAR}-{SEQ:00000}', Validators.required],
    invoiceNextSequence: [1, [Validators.required, Validators.min(1)]],
    financialYearStartMonth: [4, [Validators.required, Validators.min(1), Validators.max(12)]],
    defaultDueDays: [7, [Validators.required, Validators.min(0)]],
    currency: ['INR', Validators.required],
    taxMode: ['EXCLUSIVE', Validators.required],
    invoiceNotes: [''],
    invoiceTerms: [''],
    themePreference: ['SYSTEM', Validators.required],
  });

  constructor(
    private fb: FormBuilder,
    private businessApi: BusinessApiService,
  ) {}

  ngOnInit(): void {
    this.businessApi.getProfile().subscribe((profile) => this.profileForm.patchValue(profile as any));
    this.businessApi.getSettings().subscribe((settings) => this.settingsForm.patchValue(settings as any));
  }

  saveProfile(): void {
    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      return;
    }
    this.profileLoading.set(true);
    this.profileError.set(null);
    this.profileSaved.set(false);

    this.businessApi.updateProfile(this.profileForm.getRawValue() as any).subscribe({
      next: () => {
        this.profileLoading.set(false);
        this.profileSaved.set(true);
      },
      error: (err: HttpErrorResponse) => {
        const apiError = err.error as ApiError | undefined;
        this.profileError.set(apiError?.message ?? 'Failed to save business profile.');
        this.profileLoading.set(false);
      },
    });
  }

  saveSettings(): void {
    if (this.settingsForm.invalid) {
      this.settingsForm.markAllAsTouched();
      return;
    }
    this.settingsLoading.set(true);
    this.settingsError.set(null);
    this.settingsSaved.set(false);

    this.businessApi.updateSettings(this.settingsForm.getRawValue() as any).subscribe({
      next: () => {
        this.settingsLoading.set(false);
        this.settingsSaved.set(true);
      },
      error: (err: HttpErrorResponse) => {
        const apiError = err.error as ApiError | undefined;
        this.settingsError.set(apiError?.message ?? 'Failed to save invoice settings.');
        this.settingsLoading.set(false);
      },
    });
  }
}
