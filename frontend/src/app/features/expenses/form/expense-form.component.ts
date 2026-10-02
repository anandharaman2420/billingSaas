import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ExpenseApiService } from '../../../core/services/expense-api.service';
import { CategoryApiService } from '../../../core/services/category-api.service';
import { Category } from '../../../core/models/category.model';
import { PaymentMethod } from '../../../core/models/payment.model';
import { ApiError } from '../../../core/models/auth.model';

@Component({
  selector: 'app-expense-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './expense-form.component.html',
})
export class ExpenseFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly attachmentUploading = signal(false);
  readonly attachmentUrl = signal<string | null>(null);
  readonly categories = signal<Category[]>([]);
  expenseId: string | null = null;

  readonly paymentMethods: PaymentMethod[] = ['CASH', 'UPI', 'BANK_TRANSFER', 'CARD', 'CHEQUE', 'OTHER'];

  readonly form = this.fb.group({
    description: ['', [Validators.required, Validators.maxLength(255)]],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    expenseDate: [this.today(), Validators.required],
    paymentMethod: ['CASH' as PaymentMethod, Validators.required],
    categoryId: [''],
    referenceNumber: [''],
    notes: [''],
  });

  constructor(
    private expenseApi: ExpenseApiService,
    private categoryApi: CategoryApiService,
    private route: ActivatedRoute,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.categoryApi.list('EXPENSE').subscribe((cats) => this.categories.set(cats));

    this.expenseId = this.route.snapshot.paramMap.get('id');
    if (this.expenseId) {
      this.expenseApi.getById(this.expenseId).subscribe((expense) => {
        this.form.patchValue(expense as any);
        this.attachmentUrl.set(expense.attachmentUrl ?? null);
      });
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const raw = this.form.getRawValue();
    const request = { ...raw, categoryId: raw.categoryId || null } as any;

    const save$ = this.expenseId
      ? this.expenseApi.update(this.expenseId, request)
      : this.expenseApi.create(request);

    save$.subscribe({
      next: () => this.router.navigate(['/expenses']),
      error: (err: HttpErrorResponse) => {
        const apiError = err.error as ApiError | undefined;
        this.errorMessage.set(apiError?.message ?? 'Failed to save expense.');
        this.loading.set(false);
      },
    });
  }

  uploadAttachment(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file || !this.expenseId) return; // save the expense first, then attach a receipt

    this.attachmentUploading.set(true);
    this.expenseApi.uploadAttachment(this.expenseId, file).subscribe({
      next: (expense) => {
        this.attachmentUrl.set(expense.attachmentUrl ?? null);
        this.attachmentUploading.set(false);
      },
      error: () => this.attachmentUploading.set(false),
    });
  }

  private today(): string {
    return new Date().toISOString().substring(0, 10);
  }
}
