import { ActiveStatus } from './common.model';

export interface Customer {
  id: string;
  customerName: string;
  phone?: string;
  email?: string;
  addressLine?: string;
  city?: string;
  state?: string;
  pincode?: string;
  gstin?: string;
  notes?: string;
  status: ActiveStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CustomerRequest {
  customerName: string;
  phone?: string;
  email?: string;
  addressLine?: string;
  city?: string;
  state?: string;
  pincode?: string;
  gstin?: string;
  notes?: string;
}
