import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../models/common.model';
import { BillableService, ServiceRequest } from '../models/service.model';

@Injectable({ providedIn: 'root' })
export class ServiceApiService {
  private readonly baseUrl = `${environment.apiBaseUrl}/services`;

  constructor(private http: HttpClient) {}

  search(keyword: string, page: number, size = 25): Observable<PageResponse<BillableService>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (keyword) {
      params = params.set('keyword', keyword);
    }
    return this.http.get<PageResponse<BillableService>>(this.baseUrl, { params });
  }

  getById(id: string): Observable<BillableService> {
    return this.http.get<BillableService>(`${this.baseUrl}/${id}`);
  }

  create(request: ServiceRequest): Observable<BillableService> {
    return this.http.post<BillableService>(this.baseUrl, request);
  }

  update(id: string, request: ServiceRequest): Observable<BillableService> {
    return this.http.put<BillableService>(`${this.baseUrl}/${id}`, request);
  }

  deactivate(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/deactivate`, {});
  }

  reactivate(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/reactivate`, {});
  }
}
