export type UserRole = 'OWNER' | 'ADMIN' | 'MANAGER' | 'STAFF';

export interface AuthUser {
  id: string;
  businessId: string;
  businessName: string;
  fullName: string;
  email: string;
  role: UserRole;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresInMs: number;
  user: AuthUser;
}

export interface RegisterBusinessRequest {
  businessName: string;
  businessType?: string;
  ownerName: string;
  email: string;
  phone: string;
  password: string;
  addressLine?: string;
  city?: string;
  state?: string;
  pincode?: string;
  country?: string;
  gstin?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: { field: string; message: string }[];
}
