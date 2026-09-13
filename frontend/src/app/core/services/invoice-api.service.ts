import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../models/common.model';
import { Invoice, InvoiceRequest, InvoiceStatus, InvoiceSummary } from '../models/invoice.model';

@Injectable({ providedIn: 'root' })
export class InvoiceApiService {
  private readonly baseUrl = `${environment.apiBaseUrl}/invoices`;

  constructor(private http: HttpClient) {}

  search(
    page: number,
    size = 25,
    filters: { keyword?: string; status?: InvoiceStatus; customerId?: string } = {},
  ): Observable<PageResponse<InvoiceSummary>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters.keyword) params = params.set('keyword', filters.keyword);
    if (filters.status) params = params.set('status', filters.status);
    if (filters.customerId) params = params.set('customerId', filters.customerId);
    return this.http.get<PageResponse<InvoiceSummary>>(this.baseUrl, { params });
  }

  getById(id: string): Observable<Invoice> {
    return this.http.get<Invoice>(`${this.baseUrl}/${id}`);
  }

  create(request: InvoiceRequest): Observable<Invoice> {
    return this.http.post<Invoice>(this.baseUrl, request);
  }

  update(id: string, request: InvoiceRequest): Observable<Invoice> {
    return this.http.put<Invoice>(`${this.baseUrl}/${id}`, request);
  }

  issue(id: string): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.baseUrl}/${id}/issue`, {});
  }

  cancel(id: string, reason: string): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.baseUrl}/${id}/cancel`, { reason });
  }

  /** Opens the PDF in a new tab. The browser sends the stored JWT via the auth interceptor. */
  pdfUrl(id: string): string {
    return `${this.baseUrl}/${id}/pdf`;
  }

  downloadPdf(id: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${id}/pdf`, { responseType: 'blob' });
  }
}
