import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { InvoiceApiService } from '../../../core/services/invoice-api.service';
import { PaymentApiService } from '../../../core/services/payment-api.service';
import { AuthService } from '../../../core/auth/auth.service';
import { Invoice } from '../../../core/models/invoice.model';
import { Payment, PaymentMethod } from '../../../core/models/payment.model';

@Component({
  selector: 'app-invoice-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule],
  templateUrl: './invoice-detail.component.html',
})
export class InvoiceDetailComponent implements OnInit {
  readonly invoice = signal<Invoice | null>(null);
  readonly payments = signal<Payment[]>([]);
  readonly loading = signal(true);
  readonly actionInProgress = signal(false);
  readonly showPaymentForm = signal(false);
  readonly paymentError = signal<string | null>(null);

  readonly paymentMethods: PaymentMethod[] = ['CASH', 'UPI', 'BANK_TRANSFER', 'CARD', 'CHEQUE', 'OTHER'];

  readonly paymentForm = this.fb.group({
    amount: [0, [Validators.required, Validators.min(0.01)]],
    paymentDate: [this.today(), Validators.required],
    paymentMethod: ['CASH' as PaymentMethod, Validators.required],
    referenceNumber: [''],
    notes: [''],
  });

  constructor(
    private fb: FormBuilder,
    private invoiceApi: InvoiceApiService,
    private paymentApi: PaymentApiService,
    private route: ActivatedRoute,
    private router: Router,
    readonly authService: AuthService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.loading.set(true);
    this.invoiceApi.getById(id).subscribe({
      next: (invoice) => {
        this.invoice.set(invoice);
        this.loading.set(false);
        this.paymentForm.patchValue({ amount: invoice.balanceDue });
      },
      error: () => this.loading.set(false),
    });
    this.paymentApi.listForInvoice(id).subscribe((payments) => this.payments.set(payments));
  }

  canManage(): boolean {
    return this.authService.hasRole('OWNER', 'ADMIN', 'MANAGER');
  }

  canRecordPayment(): boolean {
    const status = this.invoice()?.status;
    return status === 'ISSUED' || status === 'PARTIALLY_PAID' || status === 'OVERDUE';
  }

  issue(): void {
    const invoice = this.invoice();
    if (!invoice || !confirm('Issue this invoice? It will be assigned a permanent invoice number.')) return;
    this.actionInProgress.set(true);
    this.invoiceApi.issue(invoice.id).subscribe({
      next: (updated) => {
        this.invoice.set(updated);
        this.actionInProgress.set(false);
      },
      error: () => this.actionInProgress.set(false),
    });
  }

  cancel(): void {
    const invoice = this.invoice();
    if (!invoice) return;
    const reason = prompt('Reason for cancelling this invoice:');
    if (!reason) return;
    this.actionInProgress.set(true);
    this.invoiceApi.cancel(invoice.id, reason).subscribe({
      next: (updated) => {
        this.invoice.set(updated);
        this.actionInProgress.set(false);
      },
      error: () => this.actionInProgress.set(false),
    });
  }

  edit(): void {
    const invoice = this.invoice();
    if (invoice) this.router.navigate(['/invoices', invoice.id, 'edit']);
  }

  togglePaymentForm(): void {
    this.showPaymentForm.set(!this.showPaymentForm());
    this.paymentError.set(null);
  }

  submitPayment(): void {
    const invoice = this.invoice();
    if (!invoice || this.paymentForm.invalid) {
      this.paymentForm.markAllAsTouched();
      return;
    }

    this.actionInProgress.set(true);
    this.paymentError.set(null);

    const raw = this.paymentForm.getRawValue();
    this.paymentApi
      .record({
        invoiceId: invoice.id,
        amount: raw.amount!,
        paymentDate: raw.paymentDate!,
        paymentMethod: raw.paymentMethod!,
        referenceNumber: raw.referenceNumber || undefined,
        notes: raw.notes || undefined,
      })
      .subscribe({
        next: () => {
          this.showPaymentForm.set(false);
          this.actionInProgress.set(false);
          this.load();
        },
        error: (err) => {
          this.paymentError.set(err.error?.message ?? 'Failed to record payment.');
          this.actionInProgress.set(false);
        },
      });
  }

  voidPayment(payment: Payment): void {
    const reason = prompt('Reason for voiding this payment:');
    if (!reason) return;
    this.actionInProgress.set(true);
    this.paymentApi.voidPayment(payment.id, reason).subscribe({
      next: () => {
        this.actionInProgress.set(false);
        this.load();
      },
      error: () => this.actionInProgress.set(false),
    });
  }

  downloadPdf(): void {
    const invoice = this.invoice();
    if (!invoice) return;
    this.invoiceApi.downloadPdf(invoice.id).subscribe((blob) => {
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${invoice.invoiceNumber ?? 'DRAFT'}.pdf`;
      a.click();
      window.URL.revokeObjectURL(url);
    });
  }

  statusColor(status: string): string {
    switch (status) {
      case 'PAID':
        return 'var(--color-success)';
      case 'CANCELLED':
        return 'var(--color-text-muted)';
      case 'OVERDUE':
        return 'var(--color-danger)';
      case 'PARTIALLY_PAID':
        return '#d97706';
      default:
        return 'var(--color-text)';
    }
  }

  private today(): string {
    return new Date().toISOString().substring(0, 10);
  }
}
