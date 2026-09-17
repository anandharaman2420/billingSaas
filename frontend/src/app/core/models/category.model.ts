export type CategoryType = 'PRODUCT' | 'SERVICE' | 'EXPENSE';

export interface Category {
  id: string;
  name: string;
  type: CategoryType;
}
