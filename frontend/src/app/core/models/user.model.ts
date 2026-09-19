import { UserRole } from './auth.model';

export interface TeamUser {
  id: string;
  fullName: string;
  email: string;
  phone?: string;
  role: UserRole;
  status: 'PENDING_ACTIVATION' | 'ACTIVE' | 'DISABLED';
  lastLoginAt?: string;
  createdAt: string;
}

export interface CreateUserRequest {
  fullName: string;
  email: string;
  phone?: string;
  password: string;
  role: UserRole;
}

export interface UpdateUserRequest {
  fullName: string;
  phone?: string;
  role: UserRole;
}

export interface UpdateOwnProfileRequest {
  fullName: string;
  phone?: string;
}
