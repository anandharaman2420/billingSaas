import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Payment, PaymentRequest } from '../models/payment.model';

@Injectable({ providedIn: 'root' })
export class PaymentApiService {
  private readonly baseUrl = `${environment.apiBaseUrl}/payments`;

  constructor(private http: HttpClient) {}

  listForInvoice(invoiceId: string): Observable<Payment[]> {
    return this.http.get<Payment[]>(`${this.baseUrl}/by-invoice/${invoiceId}`);
  }

  record(request: PaymentRequest): Observable<Payment> {
    return this.http.post<Payment>(this.baseUrl, request);
  }

  voidPayment(id: string, reason: string): Observable<Payment> {
    return this.http.post<Payment>(`${this.baseUrl}/${id}/void`, { reason });
  }
}
