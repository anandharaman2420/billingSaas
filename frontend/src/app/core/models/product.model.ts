import { ActiveStatus } from './common.model';

export interface Product {
  id: string;
  productName: string;
  sku?: string;
  categoryId?: string;
  description?: string;
  unit: string;
  purchasePrice: number;
  sellingPrice: number;
  taxRatePercent: number;
  stockQuantity: number;
  minimumStockLevel: number;
  lowStock: boolean;
  status: ActiveStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ProductRequest {
  productName: string;
  sku?: string;
  categoryId?: string;
  description?: string;
  unit: string;
  purchasePrice: number;
  sellingPrice: number;
  taxRatePercent: number;
  stockQuantity: number;
  minimumStockLevel: number;
}
