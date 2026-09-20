export interface CustomerHit {
  id: string;
  name: string;
  phone?: string;
}
export interface ProductHit {
  id: string;
  name: string;
  sku?: string;
}
export interface ServiceHit {
  id: string;
  name: string;
}
export interface InvoiceHit {
  id: string;
  invoiceNumber: string | null;
  status: string;
  grandTotal: number;
}

export interface GlobalSearchResponse {
  customers: CustomerHit[];
  products: ProductHit[];
  services: ServiceHit[];
  invoices: InvoiceHit[];
}
