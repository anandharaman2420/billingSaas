export type InvoiceStatus = 'DRAFT' | 'ISSUED' | 'PARTIALLY_PAID' | 'PAID' | 'OVERDUE' | 'CANCELLED';

export interface InvoiceItem {
  id: string;
  itemType: 'PRODUCT' | 'SERVICE';
  productId?: string;
  serviceId?: string;
  itemName: string;
  description?: string;
  quantity: number;
  unitPrice: number;
  discountAmount: number;
  taxRatePercent: number;
  taxAmount: number;
  lineTotal: number;
}

export interface Invoice {
  id: string;
  invoiceNumber: string | null;
  invoiceDate: string;
  dueDate: string | null;
  status: InvoiceStatus;
  customerId: string;
  items: InvoiceItem[];
  subtotal: number;
  itemDiscountTotal: number;
  additionalDiscountAmount: number;
  taxableAmount: number;
  cgstAmount: number;
  sgstAmount: number;
  igstAmount: number;
  totalTax: number;
  grandTotal: number;
  amountPaid: number;
  balanceDue: number;
  notes?: string;
  terms?: string;
  cancelledAt?: string;
  cancellationReason?: string;
  createdAt: string;
  updatedAt: string;
}

export interface InvoiceSummary {
  id: string;
  invoiceNumber: string | null;
  invoiceDate: string;
  dueDate: string | null;
  status: InvoiceStatus;
  customerId: string;
  grandTotal: number;
  amountPaid: number;
  balanceDue: number;
}

export interface InvoiceItemRequest {
  productId?: string | null;
  serviceId?: string | null;
  quantity: number;
  discountAmount?: number;
  description?: string;
}

export interface InvoiceRequest {
  customerId: string;
  invoiceDate: string;
  dueDate?: string | null;
  items: InvoiceItemRequest[];
  additionalDiscountAmount?: number;
  notes?: string;
  terms?: string;
}
