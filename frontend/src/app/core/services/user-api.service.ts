import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../models/common.model';
import { CreateUserRequest, TeamUser, UpdateOwnProfileRequest, UpdateUserRequest } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class UserApiService {
  private readonly baseUrl = `${environment.apiBaseUrl}/users`;

  constructor(private http: HttpClient) {}

  list(page: number, size = 25): Observable<PageResponse<TeamUser>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<TeamUser>>(this.baseUrl, { params });
  }

  getById(id: string): Observable<TeamUser> {
    return this.http.get<TeamUser>(`${this.baseUrl}/${id}`);
  }

  create(request: CreateUserRequest): Observable<TeamUser> {
    return this.http.post<TeamUser>(this.baseUrl, request);
  }

  update(id: string, request: UpdateUserRequest): Observable<TeamUser> {
    return this.http.put<TeamUser>(`${this.baseUrl}/${id}`, request);
  }

  disable(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/disable`, {});
  }

  enable(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/enable`, {});
  }

  updateOwnProfile(request: UpdateOwnProfileRequest): Observable<TeamUser> {
    return this.http.put<TeamUser>(`${this.baseUrl}/me`, request);
  }
}
