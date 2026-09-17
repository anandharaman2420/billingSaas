import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CustomerReport,
  ExpenseReport,
  InvoiceReport,
  PaymentReport,
  ProductReport,
  ReportApiService,
  SalesReport,
} from '../../core/services/report-api.service';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reports.component.html',
})
export class ReportsComponent implements OnInit {
  fromDate = this.firstOfMonth();
  toDate = this.today();

  readonly sales = signal<SalesReport | null>(null);
  readonly invoices = signal<InvoiceReport | null>(null);
  readonly payments = signal<PaymentReport | null>(null);
  readonly customers = signal<CustomerReport | null>(null);
  readonly products = signal<ProductReport | null>(null);
  readonly expenses = signal<ExpenseReport | null>(null);
  readonly loading = signal(false);

  constructor(private reportApi: ReportApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.reportApi.sales(this.fromDate, this.toDate).subscribe((r) => this.sales.set(r));
    this.reportApi.invoices().subscribe((r) => this.invoices.set(r));
    this.reportApi.payments(this.fromDate, this.toDate).subscribe((r) => this.payments.set(r));
    this.reportApi.customers(this.fromDate, this.toDate).subscribe((r) => this.customers.set(r));
    this.reportApi.products(this.fromDate, this.toDate).subscribe((r) => this.products.set(r));
    this.reportApi.expenses(this.fromDate, this.toDate).subscribe((r) => {
      this.expenses.set(r);
      this.loading.set(false);
    });
  }

  get salesCsvUrl(): string {
    return this.reportApi.salesCsvUrl(this.fromDate, this.toDate);
  }

  private today(): string {
    return new Date().toISOString().substring(0, 10);
  }

  private firstOfMonth(): string {
    const d = new Date();
    return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().substring(0, 10);
  }
}
