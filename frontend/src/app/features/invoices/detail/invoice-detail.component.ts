import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { InvoiceApiService } from '../../../core/services/invoice-api.service';
import { AuthService } from '../../../core/auth/auth.service';
import { Invoice } from '../../../core/models/invoice.model';

@Component({
  selector: 'app-invoice-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './invoice-detail.component.html',
})
export class InvoiceDetailComponent implements OnInit {
  readonly invoice = signal<Invoice | null>(null);
  readonly loading = signal(true);
  readonly actionInProgress = signal(false);

  constructor(
    private invoiceApi: InvoiceApiService,
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
      },
      error: () => this.loading.set(false),
    });
  }

  canManage(): boolean {
    return this.authService.hasRole('OWNER', 'ADMIN', 'MANAGER');
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
}
