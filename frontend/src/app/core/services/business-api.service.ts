import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  BusinessProfile,
  BusinessProfileRequest,
  BusinessSettings,
  BusinessSettingsRequest,
} from '../models/business.model';

@Injectable({ providedIn: 'root' })
export class BusinessApiService {
  private readonly baseUrl = `${environment.apiBaseUrl}/business`;

  constructor(private http: HttpClient) {}

  getProfile(): Observable<BusinessProfile> {
    return this.http.get<BusinessProfile>(`${this.baseUrl}/profile`);
  }

  updateProfile(request: BusinessProfileRequest): Observable<BusinessProfile> {
    return this.http.put<BusinessProfile>(`${this.baseUrl}/profile`, request);
  }

  getSettings(): Observable<BusinessSettings> {
    return this.http.get<BusinessSettings>(`${this.baseUrl}/settings`);
  }

  updateSettings(request: BusinessSettingsRequest): Observable<BusinessSettings> {
    return this.http.put<BusinessSettings>(`${this.baseUrl}/settings`, request);
  }
}
