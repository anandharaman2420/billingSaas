import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ServiceApiService } from '../../../core/services/service-api.service';
import { ApiError } from '../../../core/models/auth.model';

@Component({
  selector: 'app-service-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './service-form.component.html',
})
export class ServiceFormComponent implements OnInit {
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  serviceId: string | null = null;

  readonly form = this.fb.group({
    serviceName: ['', [Validators.required, Validators.maxLength(150)]],
    description: [''],
    price: [0, [Validators.required, Validators.min(0)]],
    taxRatePercent: [0, [Validators.required, Validators.min(0), Validators.max(100)]],
  });

  constructor(
    private fb: FormBuilder,
    private serviceApi: ServiceApiService,
    private route: ActivatedRoute,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.serviceId = this.route.snapshot.paramMap.get('id');
    if (this.serviceId) {
      this.serviceApi.getById(this.serviceId).subscribe((service) => this.form.patchValue(service));
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
    const save$ = this.serviceId
      ? this.serviceApi.update(this.serviceId, request)
      : this.serviceApi.create(request);

    save$.subscribe({
      next: () => this.router.navigate(['/services']),
      error: (err: HttpErrorResponse) => {
        const apiError = err.error as ApiError | undefined;
        this.errorMessage.set(apiError?.message ?? 'Failed to save service.');
        this.loading.set(false);
      },
    });
  }
}
