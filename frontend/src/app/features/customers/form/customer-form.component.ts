import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { CustomerApiService } from '../../../core/services/customer-api.service';
import { ApiError } from '../../../core/models/auth.model';

@Component({
  selector: 'app-customer-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './customer-form.component.html',
})
export class CustomerFormComponent implements OnInit {
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  customerId: string | null = null;

  readonly form = this.fb.group({
    customerName: ['', [Validators.required, Validators.maxLength(150)]],
    phone: [''],
    email: ['', [Validators.email]],
    addressLine: [''],
    city: [''],
    state: [''],
    pincode: [''],
    gstin: [''],
    notes: [''],
  });

  constructor(
    private fb: FormBuilder,
    private customerApi: CustomerApiService,
    private route: ActivatedRoute,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.customerId = this.route.snapshot.paramMap.get('id');
    if (this.customerId) {
      this.customerApi.getById(this.customerId).subscribe((customer) => this.form.patchValue(customer));
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const request = this.form.getRawValue() as any;
    const save$ = this.customerId
      ? this.customerApi.update(this.customerId, request)
      : this.customerApi.create(request);

    save$.subscribe({
      next: () => this.router.navigate(['/customers']),
      error: (err: HttpErrorResponse) => {
        const apiError = err.error as ApiError | undefined;
        this.errorMessage.set(apiError?.message ?? 'Failed to save customer.');
        this.loading.set(false);
      },
    });
  }
}
