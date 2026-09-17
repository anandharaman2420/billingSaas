import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface DailySalesPoint {
  date: string;
  total: number;
  invoiceCount: number;
}
export interface SalesReport {
  fromDate: string;
  toDate: string;
  totalSales: number;
  totalInvoices: number;
  dailyBreakdown: DailySalesPoint[];
}
export interface InvoiceStatusCount {
  status: string;
  count: number;
}
export interface InvoiceReport {
  statusCounts: InvoiceStatusCount[];
}
export interface PaymentMethodTotal {
  method: string;
  total: number;
  count: number;
}
export interface PaymentReport {
  fromDate: string;
  toDate: string;
  totalCollected: number;
  byMethod: PaymentMethodTotal[];
}
export interface TopCustomer {
  customerId: string;
  customerName: string;
  total: number;
  invoiceCount: number;
}
export interface CustomerReport {
  fromDate: string;
  toDate: string;
  topCustomers: TopCustomer[];
  totalOutstanding: number;
}
export interface TopProduct {
  productId: string;
  productName: string;
  totalQuantity: number;
  totalAmount: number;
}
export interface ProductReport {
  fromDate: string;
  toDate: string;
  topProducts: TopProduct[];
}
export interface ExpenseCategoryTotal {
  categoryId: string | null;
  categoryName: string;
  total: number;
}
export interface ExpenseReport {
  fromDate: string;
  toDate: string;
  totalExpenses: number;
  byCategory: ExpenseCategoryTotal[];
}

@Injectable({ providedIn: 'root' })
export class ReportApiService {
  private readonly baseUrl = `${environment.apiBaseUrl}/reports`;

  constructor(private http: HttpClient) {}

  private range(fromDate?: string, toDate?: string): HttpParams {
    let params = new HttpParams();
    if (fromDate) params = params.set('fromDate', fromDate);
    if (toDate) params = params.set('toDate', toDate);
    return params;
  }

  sales(fromDate?: string, toDate?: string): Observable<SalesReport> {
    return this.http.get<SalesReport>(`${this.baseUrl}/sales`, { params: this.range(fromDate, toDate) });
  }

  salesCsvUrl(fromDate?: string, toDate?: string): string {
    const params = this.range(fromDate, toDate);
    return `${this.baseUrl}/sales/export.csv?${params.toString()}`;
  }

  invoices(): Observable<InvoiceReport> {
    return this.http.get<InvoiceReport>(`${this.baseUrl}/invoices`);
  }

  payments(fromDate?: string, toDate?: string): Observable<PaymentReport> {
    return this.http.get<PaymentReport>(`${this.baseUrl}/payments`, { params: this.range(fromDate, toDate) });
  }

  customers(fromDate?: string, toDate?: string): Observable<CustomerReport> {
    return this.http.get<CustomerReport>(`${this.baseUrl}/customers`, { params: this.range(fromDate, toDate) });
  }

  products(fromDate?: string, toDate?: string): Observable<ProductReport> {
    return this.http.get<ProductReport>(`${this.baseUrl}/products`, { params: this.range(fromDate, toDate) });
  }

  expenses(fromDate?: string, toDate?: string): Observable<ExpenseReport> {
    return this.http.get<ExpenseReport>(`${this.baseUrl}/expenses`, { params: this.range(fromDate, toDate) });
  }
}
