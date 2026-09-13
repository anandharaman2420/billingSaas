import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, signal } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { InvoiceApiService } from '../../../core/services/invoice-api.service';
import { CustomerApiService } from '../../../core/services/customer-api.service';
import { ProductApiService } from '../../../core/services/product-api.service';
import { ServiceApiService } from '../../../core/services/service-api.service';
import { Customer } from '../../../core/models/customer.model';
import { Product } from '../../../core/models/product.model';
import { BillableService } from '../../../core/models/service.model';
import { ApiError } from '../../../core/models/auth.model';

/** A catalog entry the picker can offer - either a Product or a Service, normalized. */
interface CatalogEntry {
  key: string; // "product:<id>" or "service:<id>"
  type: 'PRODUCT' | 'SERVICE';
  id: string;
  name: string;
  price: number;
  taxRatePercent: number;
}

@Component({
  selector: 'app-invoice-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './invoice-form.component.html',
})
export class InvoiceFormComponent implements OnInit {
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly customers = signal<Customer[]>([]);
  readonly catalog = signal<CatalogEntry[]>([]);
  invoiceId: string | null = null;

  readonly form = this.fb.group({
    customerId: ['', Validators.required],
    invoiceDate: [this.today(), Validators.required],
    dueDate: [''],
    additionalDiscountAmount: [0, [Validators.min(0)]],
    notes: [''],
    terms: [''],
    items: this.fb.array([] as FormGroup[]),
  });

  constructor(
    private fb: FormBuilder,
    private invoiceApi: InvoiceApiService,
    private customerApi: CustomerApiService,
    private productApi: ProductApiService,
    private serviceApi: ServiceApiService,
    private route: ActivatedRoute,
    private router: Router,
  ) {}

  get items(): FormArray {
    return this.form.get('items') as FormArray;
  }

  ngOnInit(): void {
    this.customerApi.listActiveForBilling().subscribe((res) => this.customers.set(res.content));

    this.productApi.listActiveForBilling().subscribe((res) => {
      const entries = res.content.map((p) => this.toCatalogEntry(p));
      this.catalog.set([...this.catalog(), ...entries]);
    });
    this.serviceApi.listActiveForBilling().subscribe((res) => {
      const entries = res.content.map((s) => this.toCatalogEntry(s));
      this.catalog.set([...this.catalog(), ...entries]);
    });

    this.invoiceId = this.route.snapshot.paramMap.get('id');
    if (this.invoiceId) {
      this.invoiceApi.getById(this.invoiceId).subscribe((invoice) => {
        this.form.patchValue({
          customerId: invoice.customerId,
          invoiceDate: invoice.invoiceDate.substring(0, 10),
          dueDate: invoice.dueDate ? invoice.dueDate.substring(0, 10) : '',
          additionalDiscountAmount: invoice.additionalDiscountAmount,
          notes: invoice.notes,
          terms: invoice.terms,
        });
        invoice.items.forEach((item) => {
          this.items.push(
            this.fb.group({
              catalogKey: [`${item.itemType.toLowerCase()}:${item.productId ?? item.serviceId}`, Validators.required],
              quantity: [item.quantity, [Validators.required, Validators.min(0.01)]],
              discountAmount: [item.discountAmount, [Validators.min(0)]],
              description: [item.description ?? ''],
            }),
          );
        });
      });
    } else {
      this.addItem();
    }
  }

  addItem(): void {
    this.items.push(
      this.fb.group({
        catalogKey: ['', Validators.required],
        quantity: [1, [Validators.required, Validators.min(0.01)]],
        discountAmount: [0, [Validators.min(0)]],
        description: [''],
      }),
    );
  }

  removeItem(index: number): void {
    this.items.removeAt(index);
  }

  /** Client-side estimate only, for a live preview - the server recomputes authoritatively on save. */
  lineEstimate(index: number): number {
    const group = this.items.at(index);
    const entry = this.catalog().find((c) => c.key === group.get('catalogKey')?.value);
    if (!entry) return 0;
    const qty = Number(group.get('quantity')?.value) || 0;
    const discount = Number(group.get('discountAmount')?.value) || 0;
    const taxable = Math.max(qty * entry.price - discount, 0);
    const tax = (taxable * entry.taxRatePercent) / 100;
    return taxable + tax;
  }

  get subtotalEstimate(): number {
    return this.items.controls.reduce((sum, _, i) => sum + this.lineEstimate(i), 0);
  }

  entryFor(index: number): CatalogEntry | undefined {
    const key = this.items.at(index).get('catalogKey')?.value;
    return this.catalog().find((c) => c.key === key);
  }

  submit(): void {
    if (this.form.invalid || this.items.length === 0) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const raw = this.form.getRawValue();
    const request = {
      customerId: raw.customerId!,
      invoiceDate: raw.invoiceDate!,
      dueDate: raw.dueDate || null,
      additionalDiscountAmount: raw.additionalDiscountAmount || 0,
      notes: raw.notes || undefined,
      terms: raw.terms || undefined,
      items: (raw.items as any[]).map((item) => {
        const [type, id] = String(item.catalogKey).split(':');
        return {
          productId: type === 'product' ? id : null,
          serviceId: type === 'service' ? id : null,
          quantity: item.quantity,
          discountAmount: item.discountAmount || 0,
          description: item.description || undefined,
        };
      }),
    };

    const save$ = this.invoiceId
      ? this.invoiceApi.update(this.invoiceId, request)
      : this.invoiceApi.create(request);

    save$.subscribe({
      next: (invoice) => this.router.navigate(['/invoices', invoice.id]),
      error: (err: HttpErrorResponse) => {
        const apiError = err.error as ApiError | undefined;
        this.errorMessage.set(apiError?.message ?? 'Failed to save invoice.');
        this.loading.set(false);
      },
    });
  }

  private toCatalogEntry(item: Product | BillableService): CatalogEntry {
    if ('productName' in item) {
      return { key: `product:${item.id}`, type: 'PRODUCT', id: item.id, name: item.productName, price: item.sellingPrice, taxRatePercent: item.taxRatePercent };
    }
    return { key: `service:${item.id}`, type: 'SERVICE', id: item.id, name: item.serviceName, price: item.price, taxRatePercent: item.taxRatePercent };
  }

  private today(): string {
    return new Date().toISOString().substring(0, 10);
  }
}
