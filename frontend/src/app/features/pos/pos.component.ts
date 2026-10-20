import { CommonModule } from '@angular/common';
import { Component, ElementRef, HostListener, inject, OnDestroy, OnInit, signal, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PosApiService } from '../../core/services/pos-api.service';
import { CustomerApiService } from '../../core/services/customer-api.service';
import { CategoryApiService } from '../../core/services/category-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { Customer } from '../../core/models/customer.model';
import { Category } from '../../core/models/category.model';
import { PaymentMethod } from '../../core/models/payment.model';
import { PosCatalogItem, PosCartItem, PosReceipt, PosCheckoutResponse } from '../../core/models/pos.model';

@Component({
  selector: 'app-pos',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './pos.component.html',
  styleUrls: ['./pos.component.scss'],
})
export class PosComponent implements OnInit, OnDestroy {
  private readonly posApi = inject(PosApiService);
  private readonly customerApi = inject(CustomerApiService);
  private readonly categoryApi = inject(CategoryApiService);
  readonly authService = inject(AuthService);

  @ViewChild('searchInput') searchInput!: ElementRef<HTMLInputElement>;
  @ViewChild('tenderedInput') tenderedInput?: ElementRef<HTMLInputElement>;

  // Data
  readonly catalog = signal<PosCatalogItem[]>([]);
  readonly filteredCatalog = signal<PosCatalogItem[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly customers = signal<Customer[]>([]);

  // State
  readonly loading = signal(false);
  readonly checkingOut = signal(false);
  readonly errorMessage = signal<string | null>(null);

  // Filters & Search
  searchQuery = '';
  selectedCategoryId: string | null = null;

  // Cart
  readonly cart = signal<PosCartItem[]>([]);
  billDiscount = 0;
  billNotes = '';

  // Customer selection
  isWalkIn = true;
  selectedCustomerId: string | null = null;
  walkInName = '';
  walkInPhone = '';

  // Checkout Modal
  readonly showCheckoutModal = signal(false);
  selectedPaymentMethod: PaymentMethod = 'CASH';
  amountTendered = 0;
  referenceNumber = '';

  // Receipt & Success Modal
  readonly showReceiptModal = signal(false);
  readonly activeReceipt = signal<PosReceipt | null>(null);
  readonly lastCompletedResponse = signal<PosCheckoutResponse | null>(null);

  readonly paymentMethods: PaymentMethod[] = ['CASH', 'UPI', 'CARD', 'BANK_TRANSFER', 'CHEQUE', 'OTHER'];
  readonly quickNotes = [100, 200, 500, 1000, 2000];
  private alertTimeout?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    this.loadCatalog();
    this.loadCategories();
    this.loadCustomers();
  }

  ngOnDestroy(): void {
    this.clearAlertTimeout();
  }

  private showAlert(message: string): void {
    this.clearAlertTimeout();
    this.errorMessage.set(message);
    this.alertTimeout = setTimeout(() => this.dismissAlert(), 5000);
  }

  dismissAlert(): void {
    this.clearAlertTimeout();
    this.errorMessage.set(null);
  }

  private clearAlertTimeout(): void {
    if (this.alertTimeout) {
      clearTimeout(this.alertTimeout);
      this.alertTimeout = undefined;
    }
  }

  loadCatalog(): void {
    this.loading.set(true);
    this.posApi.getCatalog().subscribe({
      next: (items) => {
        this.catalog.set(items);
        this.applyFilter();
        this.loading.set(false);
      },
      error: () => {
        this.showAlert('Could not load products. Please try again.');
        this.loading.set(false);
      },
    });
  }

  loadCategories(): void {
    this.categoryApi.list('PRODUCT').subscribe({
      next: (cats) => this.categories.set(cats),
      error: () => {},
    });
  }

  loadCustomers(): void {
    this.customerApi.listActiveForBilling().subscribe({
      next: (res) => this.customers.set(res.content),
      error: () => {},
    });
  }

  applyFilter(): void {
    const q = this.searchQuery.trim().toLowerCase();
    const catId = this.selectedCategoryId;

    const filtered = this.catalog().filter((item) => {
      const matchCat = !catId || item.categoryId === catId;
      const matchQuery =
        !q ||
        item.name.toLowerCase().includes(q) ||
        (item.sku && item.sku.toLowerCase().includes(q)) ||
        (item.hsnSacCode && item.hsnSacCode.toLowerCase().includes(q));
      return matchCat && matchQuery;
    });

    this.filteredCatalog.set(filtered);
  }

  onSearchChange(): void {
    this.applyFilter();
  }

  onSearchKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter') {
      const query = this.searchQuery.trim().toLowerCase();
      if (!query) return;

      // Check for exact SKU/barcode match
      const exactMatch = this.catalog().find(
        (i) => i.sku && i.sku.toLowerCase() === query
      );
      if (exactMatch) {
        this.addToCart(exactMatch);
        this.searchQuery = '';
        this.applyFilter();
        return;
      }

      // If only 1 item filtered, add it
      if (this.filteredCatalog().length === 1) {
        this.addToCart(this.filteredCatalog()[0]);
        this.searchQuery = '';
        this.applyFilter();
      }
    }
  }

  selectCategory(categoryId: string | null): void {
    this.selectedCategoryId = categoryId;
    this.applyFilter();
  }

  // --- Cart Management ---

  addToCart(item: PosCatalogItem): void {
    const current = [...this.cart()];
    const index = current.findIndex((c) => c.catalogItem.id === item.id);
    const nextQuantity = index >= 0 ? current[index].quantity + 1 : 1;

    if (!this.hasAvailableStock(item, nextQuantity)) {
      return;
    }

    if (index >= 0) {
      current[index] = {
        ...current[index],
        quantity: current[index].quantity + 1,
      };
    } else {
      current.push({
        catalogItem: item,
        quantity: 1,
        discountAmount: 0,
      });
    }

    this.cart.set(current);
    this.dismissAlert();
  }

  incrementQty(index: number): void {
    const current = [...this.cart()];
    const item = current[index];
    if (!item || !this.hasAvailableStock(item.catalogItem, item.quantity + 1)) {
      return;
    }

    current[index] = {
      ...item,
      quantity: item.quantity + 1,
    };
    this.cart.set(current);
  }

  decrementQty(index: number): void {
    const current = [...this.cart()];
    if (current[index].quantity > 1) {
      current[index] = {
        ...current[index],
        quantity: current[index].quantity - 1,
      };
      this.cart.set(current);
    } else {
      this.removeFromCart(index);
    }
  }

  updateQty(index: number, newQty: number): void {
    if (newQty <= 0) {
      this.removeFromCart(index);
      return;
    }
    const current = [...this.cart()];
    const item = current[index];
    if (!item || !Number.isFinite(newQty) || !this.hasAvailableStock(item.catalogItem, newQty)) {
      return;
    }

    current[index] = {
      ...item,
      quantity: newQty,
    };
    this.cart.set(current);
  }

  private hasAvailableStock(item: PosCatalogItem, requestedQuantity: number): boolean {
    if (item.type !== 'PRODUCT') {
      return true;
    }

    const currentItem = this.catalog().find((catalogItem) => catalogItem.id === item.id);
    const availableQuantity = currentItem?.stockQuantity ?? item.stockQuantity;
    if (requestedQuantity <= availableQuantity) {
      return true;
    }

    this.showAlert(
      availableQuantity <= 0
        ? `Out of stock — ${item.name} can't be added to this bill.`
        : `Not enough stock — only ${availableQuantity} ${item.unit || 'pcs'} of ${item.name} available. The bill was not changed.`
    );
    return false;
  }

  private validateCartStock(): boolean {
    return this.cart().every((item) => this.hasAvailableStock(item.catalogItem, item.quantity));
  }

  updateLineDiscount(index: number, discount: number): void {
    const current = [...this.cart()];
    current[index] = {
      ...current[index],
      discountAmount: Math.max(0, Number(discount) || 0),
    };
    this.cart.set(current);
  }

  removeFromCart(index: number): void {
    const current = [...this.cart()];
    current.splice(index, 1);
    this.cart.set(current);
  }

  clearCart(): void {
    this.cart.set([]);
    this.billDiscount = 0;
    this.billNotes = '';
    this.walkInName = '';
    this.walkInPhone = '';
    this.selectedCustomerId = null;
    this.isWalkIn = true;
    this.dismissAlert();
    if (this.searchInput) {
      this.searchInput.nativeElement.focus();
    }
  }

  // --- Financial Computations ---

  get subtotal(): number {
    return this.cart().reduce((sum, item) => {
      const lineNet = item.catalogItem.price * item.quantity - item.discountAmount;
      return sum + Math.max(0, lineNet);
    }, 0);
  }

  get totalTax(): number {
    return this.cart().reduce((sum, item) => {
      const lineNet = Math.max(0, item.catalogItem.price * item.quantity - item.discountAmount);
      const tax = (lineNet * item.catalogItem.taxRatePercent) / 100;
      return sum + tax;
    }, 0);
  }

  get grandTotal(): number {
    const total = this.subtotal + this.totalTax - (this.billDiscount || 0);
    return Math.max(0, Math.round(total * 100) / 100);
  }

  get changeAmount(): number {
    if (this.amountTendered > this.grandTotal) {
      return Math.round((this.amountTendered - this.grandTotal) * 100) / 100;
    }
    return 0;
  }

  // --- Checkout Flows ---

  quickCheckout(method: PaymentMethod): void {
    if (this.cart().length === 0 || !this.validateCartStock()) return;
    this.selectedPaymentMethod = method;
    this.amountTendered = this.grandTotal;
    this.referenceNumber = '';
    this.executeCheckout();
  }

  openPaymentModal(): void {
    if (this.cart().length === 0 || !this.validateCartStock()) return;
    this.amountTendered = this.grandTotal;
    this.referenceNumber = '';
    this.selectedPaymentMethod = 'CASH';
    this.showCheckoutModal.set(true);
    setTimeout(() => {
      this.tenderedInput?.nativeElement.focus();
    }, 100);
  }

  closePaymentModal(): void {
    this.showCheckoutModal.set(false);
  }

  setTendered(amount: number): void {
    this.amountTendered = amount;
  }

  executeCheckout(): void {
    if (this.cart().length === 0 || !this.validateCartStock()) return;

    this.checkingOut.set(true);
    this.dismissAlert();

    const items = this.cart().map((c) => ({
      productId: c.catalogItem.type === 'PRODUCT' ? c.catalogItem.id : undefined,
      serviceId: c.catalogItem.type === 'SERVICE' ? c.catalogItem.id : undefined,
      quantity: c.quantity,
      discountAmount: c.discountAmount > 0 ? c.discountAmount : undefined,
    }));

    const payload = {
      customerId: !this.isWalkIn && this.selectedCustomerId ? this.selectedCustomerId : undefined,
      customerName: this.isWalkIn && this.walkInName ? this.walkInName.trim() : undefined,
      customerPhone: this.isWalkIn && this.walkInPhone ? this.walkInPhone.trim() : undefined,
      items,
      additionalDiscountAmount: this.billDiscount > 0 ? this.billDiscount : undefined,
      paymentMethod: this.selectedPaymentMethod,
      amountTendered: this.amountTendered >= this.grandTotal ? this.amountTendered : this.grandTotal,
      amountPaid: this.grandTotal,
      referenceNumber: this.referenceNumber ? this.referenceNumber.trim() : undefined,
      notes: this.billNotes ? this.billNotes.trim() : undefined,
    };

    this.posApi.checkout(payload).subscribe({
      next: (resp) => {
        this.checkingOut.set(false);
        this.showCheckoutModal.set(false);
        this.lastCompletedResponse.set(resp);
        this.activeReceipt.set(resp.receipt);
        this.showReceiptModal.set(true);
        // Refresh catalog to update stock quantities
        this.loadCatalog();
      },
      error: (err) => {
        this.checkingOut.set(false);
        this.showAlert(err.error?.message || 'Checkout failed. Please try again.');
      },
    });
  }

  printReceipt(): void {
    document.body.classList.add('pos-print-mode');
    window.addEventListener(
      'afterprint',
      () => document.body.classList.remove('pos-print-mode'),
      { once: true }
    );
    window.print();
  }

  nextSale(): void {
    this.showReceiptModal.set(false);
    this.activeReceipt.set(null);
    this.lastCompletedResponse.set(null);
    this.clearCart();
  }

  @HostListener('window:keydown', ['$event'])
  handleGlobalShortcuts(event: KeyboardEvent): void {
    if (event.key === 'F2') {
      event.preventDefault();
      if (!this.showCheckoutModal() && !this.showReceiptModal() && this.cart().length > 0) {
        this.openPaymentModal();
      }
    } else if (event.key === 'Escape') {
      if (this.showReceiptModal()) {
        this.nextSale();
      } else if (this.showCheckoutModal()) {
        this.closePaymentModal();
      }
    }
  }
}
