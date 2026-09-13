import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../models/common.model';
import { Product, ProductRequest } from '../models/product.model';

@Injectable({ providedIn: 'root' })
export class ProductApiService {
  private readonly baseUrl = `${environment.apiBaseUrl}/products`;

  constructor(private http: HttpClient) {}

  search(keyword: string, page: number, size = 25): Observable<PageResponse<Product>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (keyword) {
      params = params.set('keyword', keyword);
    }
    return this.http.get<PageResponse<Product>>(this.baseUrl, { params });
  }

  /** Used by the invoice line-item picker - all active products, no pagination UI needed for a dropdown. */
  listActiveForBilling(keyword = ''): Observable<PageResponse<Product>> {
    let params = new HttpParams().set('page', 0).set('size', 200).set('status', 'ACTIVE');
    if (keyword) {
      params = params.set('keyword', keyword);
    }
    return this.http.get<PageResponse<Product>>(this.baseUrl, { params });
  }

  getById(id: string): Observable<Product> {
    return this.http.get<Product>(`${this.baseUrl}/${id}`);
  }

  create(request: ProductRequest): Observable<Product> {
    return this.http.post<Product>(this.baseUrl, request);
  }

  update(id: string, request: ProductRequest): Observable<Product> {
    return this.http.put<Product>(`${this.baseUrl}/${id}`, request);
  }

  deactivate(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/deactivate`, {});
  }

  reactivate(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/reactivate`, {});
  }
}
