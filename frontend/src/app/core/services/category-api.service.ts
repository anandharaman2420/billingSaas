import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Category, CategoryType } from '../models/category.model';

@Injectable({ providedIn: 'root' })
export class CategoryApiService {
  private readonly baseUrl = `${environment.apiBaseUrl}/categories`;

  constructor(private http: HttpClient) {}

  list(type: CategoryType): Observable<Category[]> {
    const params = new HttpParams().set('type', type);
    return this.http.get<Category[]>(this.baseUrl, { params });
  }

  create(name: string, type: CategoryType): Observable<Category> {
    return this.http.post<Category>(this.baseUrl, { name, type });
  }
}
