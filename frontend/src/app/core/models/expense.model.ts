import { PaymentMethod } from './payment.model';

export interface Expense {
  id: string;
  description: string;
  amount: number;
  expenseDate: string;
  paymentMethod: PaymentMethod;
  categoryId?: string;
  referenceNumber?: string;
  notes?: string;
  attachmentUrl?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ExpenseRequest {
  description: string;
  amount: number;
  expenseDate: string;
  paymentMethod: PaymentMethod;
  categoryId?: string | null;
  referenceNumber?: string;
  notes?: string;
  attachmentUrl?: string;
}
