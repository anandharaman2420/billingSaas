import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Branch, BranchRequest } from '../models/branch.model';

@Injectable({ providedIn: 'root' })
export class BranchApiService {
  private readonly url = `${environment.apiBaseUrl}/branches`;
  private readonly storageKey = 'active-branch-id';

  constructor(private http: HttpClient) {}

  list(): Observable<Branch[]> {
    return this.http.get<Branch[]>(this.url);
  }

  listAll(): Observable<Branch[]> {
    return this.http.get<Branch[]>(`${this.url}/all`);
  }

  create(request: BranchRequest): Observable<Branch> {
    return this.http.post<Branch>(this.url, request);
  }

  update(id: string, request: BranchRequest): Observable<Branch> {
    return this.http.put<Branch>(`${this.url}/${id}`, request);
  }

  deactivate(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  reactivate(id: string): Observable<Branch> {
    return this.http.post<Branch>(`${this.url}/${id}/reactivate`, {});
  }
  selectedId(): string | null {
    return localStorage.getItem(this.storageKey);
  }

  select(id: string): void {
    localStorage.setItem(this.storageKey, id);
  }
}
