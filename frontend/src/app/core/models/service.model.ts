import { ActiveStatus } from './common.model';

export interface BillableService {
  id: string;
  serviceName: string;
  description?: string;
  price: number;
  taxRatePercent: number;
  categoryId?: string;
  status: ActiveStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ServiceRequest {
  serviceName: string;
  description?: string;
  price: number;
  taxRatePercent: number;
  categoryId?: string;
}
