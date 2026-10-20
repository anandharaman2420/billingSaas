export interface Branch {
  id: string;
  branchName: string;
  addressLine?: string;
  city?: string;
  state?: string;
  pincode?: string;
  country?: string;
  phone?: string;
  email?: string;
  gstin?: string;
  defaultBranch: boolean;
  status: string;
}

export type BranchRequest = Omit<Branch, 'id' | 'defaultBranch' | 'status'>;
