import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { SubscriptionApiService } from '../../core/services/subscription-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { Subscription } from '../../core/models/business.model';
import { ApiError } from '../../core/models/auth.model';
import { RazorpayCheckoutService } from '../../core/services/razorpay-checkout.service';
import { BranchApiService } from '../../core/services/branch-api.service';
import { Branch, BranchRequest } from '../../core/models/branch.model';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './settings.component.html',
})
export class SettingsComponent implements OnInit {
  private readonly fb = inject(FormBuilder);

  readonly profileLoading = signal(false);
  readonly profileSaved = signal(false);
  readonly profileError = signal<string | null>(null);

  readonly settingsLoading = signal(false);
  readonly settingsSaved = signal(false);
  readonly settingsError = signal<string | null>(null);

  readonly logoUploading = signal(false);
  readonly logoError = signal<string | null>(null);

  readonly planChangeError = signal<string | null>(null);

  readonly subscription = signal<Subscription | null>(null);
  readonly plans = ['FREE', 'BASIC', 'PROFESSIONAL', 'BUSINESS'];
  readonly planChanging = signal(false);
  readonly gateways = signal<('RAZORPAY' | 'STRIPE')[]>([]);
  readonly checkoutMessage = signal<string | null>(null);
  readonly branches = signal<Branch[]>([]);
  readonly branchSaving = signal(false);
  readonly branchError = signal<string | null>(null);
  readonly editingBranchId = signal<string | null>(null);
  private readonly razorpayCheckout = inject(RazorpayCheckoutService);
  private readonly branchApi = inject(BranchApiService);

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
    invoiceTemplate: ['CLASSIC', Validators.required],
    posPrefix: ['BILL'],
    posNumberFormat: ['{PREFIX}-{YEAR}-{SEQ:00000}'],
    posNextSequence: [1, [Validators.min(1)]],
    financialYearStartMonth: [4, [Validators.required, Validators.min(1), Validators.max(12)]],
    defaultDueDays: [7, [Validators.required, Validators.min(0)]],
    currency: ['INR', Validators.required],
    taxMode: ['EXCLUSIVE', Validators.required],
    invoiceNotes: [''],
    invoiceTerms: [''],
    themePreference: ['SYSTEM', Validators.required],
  });

  readonly branchForm = this.fb.group({
    branchName: ['', Validators.required],
    addressLine: [''],
    city: [''],
    state: [''],
    pincode: [''],
    country: ['India'],
    phone: [''],
    email: ['', Validators.email],
    gstin: [''],
  });

  constructor(
    private businessApi: BusinessApiService,
    private subscriptionApi: SubscriptionApiService,
    readonly authService: AuthService,
  ) {}

  ngOnInit(): void {
    this.businessApi.getProfile().subscribe((profile) => this.profileForm.patchValue(profile as any));
    this.businessApi.getSettings().subscribe((settings) => this.settingsForm.patchValue(settings as any));
    this.subscriptionApi.get().subscribe((sub) => this.subscription.set(sub));
    this.subscriptionApi.gateways().subscribe((gateways) => this.gateways.set(gateways.providers));
    this.loadBranches();
  }

  changePlan(plan: string): void {
    if (!this.authService.hasRole('OWNER')) return;
    this.planChanging.set(true);
    this.planChangeError.set(null);
    this.subscriptionApi.changePlan(plan).subscribe({
      next: (sub) => {
        this.subscription.set(sub);
        this.planChanging.set(false);
      },
      error: (err: HttpErrorResponse) => {
        // A configured payment gateway rejects free self-grants of a paid
        // plan - surface that clearly and point at checkout instead.
        const apiError = err.error as ApiError | undefined;
        this.planChangeError.set(apiError?.message ?? 'Failed to change plan.');
        this.planChanging.set(false);
      },
    });
  }

  startCheckout(plan: string, provider: 'RAZORPAY' | 'STRIPE'): void {
    if (!this.authService.hasRole('OWNER')) return;
    this.planChanging.set(true);
    this.planChangeError.set(null);
    this.checkoutMessage.set(null);
    this.subscriptionApi.checkout(plan, provider).subscribe({
      next: async (checkout) => {
        try {
          if (checkout.provider === 'STRIPE') {
            if (!checkout.checkoutUrl) throw new Error('Stripe did not return a checkout URL.');
            window.location.assign(checkout.checkoutUrl);
            return;
          }
          if (!checkout.keyId) throw new Error('Razorpay did not return a checkout key.');
          await this.razorpayCheckout.open({
            key: checkout.keyId,
            amount: checkout.amountPaise,
            currency: checkout.currency,
            name: 'BillingSaaS',
            description: `${checkout.plan} monthly subscription`,
            order_id: checkout.orderId,
            handler: () => {
              this.checkoutMessage.set('Payment received. Your plan will update after payment verification.');
              this.planChanging.set(false);
              this.subscriptionApi.get().subscribe((sub) => this.subscription.set(sub));
            },
            modal: { ondismiss: () => this.planChanging.set(false) },
          });
        } catch (error) {
          this.planChangeError.set(error instanceof Error ? error.message : 'Unable to open checkout.');
          this.planChanging.set(false);
        }
      },
      error: (err: HttpErrorResponse) => {
        const apiError = err.error as ApiError | undefined;
        this.planChangeError.set(apiError?.message ?? 'Checkout is not available.');
        this.planChanging.set(false);
      },
    });
  }

  loadBranches(): void {
    this.branchApi.listAll().subscribe({
      next: (branches) => this.branches.set(branches),
      error: (err: HttpErrorResponse) => {
        const apiError = err.error as ApiError | undefined;
        this.branchError.set(apiError?.message ?? 'Failed to load branches.');
      },
    });
  }

  editBranch(branch: Branch): void {
    if (branch.status !== 'ACTIVE') return;
    this.editingBranchId.set(branch.id);
    this.branchForm.patchValue(branch);
    this.branchError.set(null);
  }

  resetBranchForm(): void {
    this.editingBranchId.set(null);
    this.branchForm.reset({
      branchName: '',
      addressLine: '',
      city: '',
      state: '',
      pincode: '',
      country: 'India',
      phone: '',
      email: '',
      gstin: '',
    });
  }

  saveBranch(): void {
    if (!this.authService.hasRole('OWNER', 'ADMIN')) return;
    if (this.branchForm.invalid) {
      this.branchForm.markAllAsTouched();
      return;
    }
    this.branchSaving.set(true);
    this.branchError.set(null);
    const raw = this.branchForm.getRawValue();
    const request: BranchRequest = {
      branchName: raw.branchName ?? '',
      addressLine: raw.addressLine ?? undefined,
      city: raw.city ?? undefined,
      state: raw.state ?? undefined,
      pincode: raw.pincode ?? undefined,
      country: raw.country ?? undefined,
      phone: raw.phone ?? undefined,
      email: raw.email ?? undefined,
      gstin: raw.gstin ?? undefined,
    };
    const branchId = this.editingBranchId();
    const save = branchId ? this.branchApi.update(branchId, request) : this.branchApi.create(request);
    save.subscribe({
      next: () => {
        this.branchSaving.set(false);
        this.resetBranchForm();
        this.loadBranches();
      },
      error: (err: HttpErrorResponse) => {
        const apiError = err.error as ApiError | undefined;
        this.branchError.set(apiError?.message ?? 'Failed to save branch.');
        this.branchSaving.set(false);
      },
    });
  }

  deactivateBranch(branch: Branch): void {
    if (!this.authService.hasRole('OWNER', 'ADMIN') || branch.defaultBranch || branch.status !== 'ACTIVE') return;
    if (!window.confirm(`Deactivate ${branch.branchName}? Its existing records will be preserved.`)) return;
    this.branchApi.deactivate(branch.id).subscribe({
      next: () => {
        const active = this.branchApi.selectedId();
        const defaultBranch = this.branches().find((item) => item.defaultBranch);
        if (active === branch.id && defaultBranch) {
          this.branchApi.select(defaultBranch.id);
          window.location.reload();
          return;
        }
        this.loadBranches();
      },
      error: (err: HttpErrorResponse) => {
        const apiError = err.error as ApiError | undefined;
        this.branchError.set(apiError?.message ?? 'Failed to deactivate branch.');
      },
    });
  }

  reactivateBranch(branch: Branch): void {
    if (!this.authService.hasRole('OWNER', 'ADMIN') || branch.status === 'ACTIVE') return;
    this.branchApi.reactivate(branch.id).subscribe({
      next: () => this.loadBranches(),
      error: (err: HttpErrorResponse) => {
        const apiError = err.error as ApiError | undefined;
        this.branchError.set(apiError?.message ?? 'Failed to reactivate branch.');
      },
    });
  }

  uploadLogo(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.logoUploading.set(true);
    this.logoError.set(null);

    this.businessApi.uploadLogo(file).subscribe({
      next: (profile) => {
        this.profileForm.patchValue({ logoUrl: profile.logoUrl });
        this.logoUploading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        const apiError = err.error as ApiError | undefined;
        this.logoError.set(apiError?.message ?? 'Failed to upload logo.');
        this.logoUploading.set(false);
      },
    });
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
