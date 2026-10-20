import { PaymentMethod } from './payment.model';
import { Invoice } from './invoice.model';
import { Payment } from './payment.model';

export interface PosCatalogItem {
  id: string;
  name: string;
  type: 'PRODUCT' | 'SERVICE';
  sku?: string;
  hsnSacCode?: string;
  unit?: string;
  price: number;
  taxRatePercent: number;
  stockQuantity: number;
  categoryId?: string;
  categoryName?: string;
}

export interface PosItemRequest {
  productId?: string;
  serviceId?: string;
  quantity: number;
  unitPrice?: number;
  discountAmount?: number;
  description?: string;
}

export interface PosCheckoutRequest {
  customerId?: string;
  customerName?: string;
  customerPhone?: string;
  items: PosItemRequest[];
  additionalDiscountAmount?: number;
  paymentMethod: PaymentMethod;
  amountTendered?: number;
  amountPaid?: number;
  referenceNumber?: string;
  notes?: string;
}

export interface PosReceiptItem {
  itemName: string;
  itemType: string;
  hsnSacCode?: string;
  quantity: number;
  unitPrice: number;
  discountAmount: number;
  taxRatePercent: number;
  taxAmount: number;
  lineTotal: number;
}

export interface PosReceipt {
  invoiceId: string;
  invoiceNumber: string;
  billNo?: string;
  invoiceDate: string;
  createdAt: string;
  cashierName: string;

  businessName: string;
  branchName?: string;
  businessAddress: string;
  businessPhone?: string;
  businessEmail?: string;
  businessGstin?: string;

  customerName: string;
  customerPhone?: string;

  items: PosReceiptItem[];

  subtotal: number;
  itemDiscountTotal: number;
  additionalDiscountAmount: number;
  taxableAmount: number;
  cgstAmount: number;
  sgstAmount: number;
  igstAmount: number;
  totalTax: number;
  grandTotal: number;

  paymentMethod: string;
  amountPaid: number;
  amountTendered: number;
  changeAmount: number;
  paymentReference?: string;

  currency: string;
  notes?: string;
  terms?: string;
}

export interface PosCheckoutResponse {
  invoice: Invoice;
  payment?: Payment;
  changeAmount: number;
  receipt: PosReceipt;
}

export interface PosCartItem {
  catalogItem: PosCatalogItem;
  quantity: number;
  discountAmount: number;
}
