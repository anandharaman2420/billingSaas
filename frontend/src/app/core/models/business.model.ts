export interface BusinessProfile {
  id: string;
  businessName: string;
  businessType?: string;
  ownerName: string;
  addressLine?: string;
  city?: string;
  state?: string;
  pincode?: string;
  country?: string;
  phone?: string;
  email?: string;
  website?: string;
  logoUrl?: string;
  gstin?: string;
  plan: string;
  status: string;
}

export interface BusinessProfileRequest {
  businessName: string;
  businessType?: string;
  addressLine?: string;
  city?: string;
  state?: string;
  pincode?: string;
  country?: string;
  phone?: string;
  email?: string;
  website?: string;
  logoUrl?: string;
  gstin?: string;
}

export interface BusinessSettings {
  invoicePrefix: string;
  invoiceNumberFormat: string;
  invoiceNextSequence: number;
  financialYearStartMonth: number;
  defaultDueDays: number;
  currency: string;
  taxMode: 'EXCLUSIVE' | 'INCLUSIVE';
  invoiceNotes?: string;
  invoiceTerms?: string;
  themePreference: 'LIGHT' | 'DARK' | 'SYSTEM';
}

export type BusinessSettingsRequest = BusinessSettings;
