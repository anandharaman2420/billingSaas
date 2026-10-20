import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Subscription } from '../models/business.model';

@Injectable({ providedIn: 'root' })
export class SubscriptionApiService {
  private readonly baseUrl = `${environment.apiBaseUrl}/subscription`;

  constructor(private http: HttpClient) {}

  get(): Observable<Subscription> {
    return this.http.get<Subscription>(this.baseUrl);
  }

  changePlan(plan: string): Observable<Subscription> {
    return this.http.put<Subscription>(`${this.baseUrl}/plan`, { plan });
  }

  checkout(plan: string, provider: 'RAZORPAY' | 'STRIPE'): Observable<CheckoutResponse> {
    return this.http.post<CheckoutResponse>(`${this.baseUrl}/checkout`, { plan, provider });
  }

  gateways(): Observable<{ providers: ('RAZORPAY' | 'STRIPE')[] }> {
    return this.http.get<{ providers: ('RAZORPAY' | 'STRIPE')[] }>(`${this.baseUrl}/gateways`);
  }
}

export interface CheckoutResponse {
  provider: 'RAZORPAY' | 'STRIPE';
  orderId: string;
  amountPaise: number;
  currency: string;
  keyId: string | null;
  checkoutUrl: string | null;
  plan: string;
}
