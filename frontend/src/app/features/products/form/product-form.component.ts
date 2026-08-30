import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ProductApiService } from '../../../core/services/product-api.service';
import { ApiError } from '../../../core/models/auth.model';

@Component({
  selector: 'app-product-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './product-form.component.html',
})
export class ProductFormComponent implements OnInit {
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  productId: string | null = null;

  readonly form = this.fb.group({
    productName: ['', [Validators.required, Validators.maxLength(150)]],
    sku: [''],
    description: [''],
    unit: ['PCS', [Validators.required]],
    purchasePrice: [0, [Validators.required, Validators.min(0)]],
    sellingPrice: [0, [Validators.required, Validators.min(0)]],
    taxRatePercent: [0, [Validators.required, Validators.min(0), Validators.max(100)]],
    stockQuantity: [0, [Validators.required, Validators.min(0)]],
    minimumStockLevel: [0, [Validators.required, Validators.min(0)]],
  });

  constructor(
    private fb: FormBuilder,
    private productApi: ProductApiService,
    private route: ActivatedRoute,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.productId = this.route.snapshot.paramMap.get('id');
    if (this.productId) {
      this.productApi.getById(this.productId).subscribe((product) => this.form.patchValue(product));
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
    const save$ = this.productId
      ? this.productApi.update(this.productId, request)
      : this.productApi.create(request);

    save$.subscribe({
      next: () => this.router.navigate(['/products']),
      error: (err: HttpErrorResponse) => {
        const apiError = err.error as ApiError | undefined;
        this.errorMessage.set(apiError?.message ?? 'Failed to save product.');
        this.loading.set(false);
      },
    });
  }
}
