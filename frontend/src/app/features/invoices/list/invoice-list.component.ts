import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';
import { InvoiceApiService } from '../../../core/services/invoice-api.service';
import { InvoiceStatus, InvoiceSummary } from '../../../core/models/invoice.model';
import { PageResponse } from '../../../core/models/common.model';

@Component({
  selector: 'app-invoice-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './invoice-list.component.html',
})
export class InvoiceListComponent implements OnInit {
  readonly result = signal<PageResponse<InvoiceSummary> | null>(null);
  readonly loading = signal(false);
  keyword = '';
  status: InvoiceStatus | '' = '';
  page = 0;

  readonly statuses: InvoiceStatus[] = ['DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'CANCELLED'];

  private readonly keywordChanged = new Subject<string>();

  constructor(private invoiceApi: InvoiceApiService) {
    this.keywordChanged.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => {
      this.page = 0;
      this.load();
    });
  }

  ngOnInit(): void {
    this.load();
  }

  onKeywordChange(): void {
    this.keywordChanged.next(this.keyword);
  }

  onStatusChange(): void {
    this.page = 0;
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.invoiceApi
      .search(this.page, 25, { keyword: this.keyword, status: this.status || undefined })
      .subscribe({
        next: (res) => {
          this.result.set(res);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  nextPage(): void {
    if (!this.result()?.last) {
      this.page++;
      this.load();
    }
  }

  prevPage(): void {
    if (!this.result()?.first) {
      this.page--;
      this.load();
    }
  }

  statusColor(status: InvoiceStatus): string {
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
