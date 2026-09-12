import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';
import { ProductApiService } from '../../../core/services/product-api.service';
import { Product } from '../../../core/models/product.model';
import { PageResponse } from '../../../core/models/common.model';

@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './product-list.component.html',
})
export class ProductListComponent implements OnInit {
  readonly result = signal<PageResponse<Product> | null>(null);
  readonly loading = signal(false);
  keyword = '';
  page = 0;

  private readonly keywordChanged = new Subject<string>();

  constructor(private productApi: ProductApiService) {
    this.keywordChanged.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => {
      this.page = 0;
      this.load();
    });
  }

  ngOnInit(): void {
    this.load();
  }

  onKeywordChange(): void {
    this.keywordChanged.next(this.keyword);
  }

  load(): void {
    this.loading.set(true);
    this.productApi.search(this.keyword, this.page).subscribe({
      next: (res) => {
        this.result.set(res);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  nextPage(): void {
    if (!this.result()?.last) {
      this.page++;
      this.load();
    }
  }

  prevPage(): void {
    if (!this.result()?.first) {
      this.page--;
      this.load();
    }
  }

  deactivate(product: Product): void {
    if (!confirm(`Deactivate ${product.productName}?`)) return;
    this.productApi.deactivate(product.id).subscribe(() => this.load());
  }

  reactivate(product: Product): void {
    this.productApi.reactivate(product.id).subscribe(() => this.load());
  }
}
