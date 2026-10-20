import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PosCatalogItem, PosCheckoutRequest, PosCheckoutResponse, PosReceipt } from '../models/pos.model';

@Injectable({ providedIn: 'root' })
export class PosApiService {
  private readonly baseUrl = `${environment.apiBaseUrl}/pos`;

  constructor(private http: HttpClient) {}

  getCatalog(keyword?: string, categoryId?: string): Observable<PosCatalogItem[]> {
    let params = new HttpParams();
    if (keyword && keyword.trim()) {
      params = params.set('keyword', keyword.trim());
    }
    if (categoryId) {
      params = params.set('categoryId', categoryId);
    }
    return this.http.get<PosCatalogItem[]>(`${this.baseUrl}/catalog`, { params });
  }

  checkout(request: PosCheckoutRequest): Observable<PosCheckoutResponse> {
    return this.http.post<PosCheckoutResponse>(`${this.baseUrl}/checkout`, request);
  }

  getReceipt(invoiceId: string): Observable<PosReceipt> {
    return this.http.get<PosReceipt>(`${this.baseUrl}/receipt/${invoiceId}`);
  }
}
