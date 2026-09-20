import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { GlobalSearchResponse } from '../models/search.model';

@Injectable({ providedIn: 'root' })
export class SearchApiService {
  private readonly baseUrl = `${environment.apiBaseUrl}/search`;

  constructor(private http: HttpClient) {}

  search(query: string): Observable<GlobalSearchResponse> {
    const params = new HttpParams().set('q', query);
    return this.http.get<GlobalSearchResponse>(this.baseUrl, { params });
  }
}
