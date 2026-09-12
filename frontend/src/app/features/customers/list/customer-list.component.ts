import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';
import { CustomerApiService } from '../../../core/services/customer-api.service';
import { Customer } from '../../../core/models/customer.model';
import { PageResponse } from '../../../core/models/common.model';

@Component({
  selector: 'app-customer-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './customer-list.component.html',
})
export class CustomerListComponent implements OnInit {
  readonly result = signal<PageResponse<Customer> | null>(null);
  readonly loading = signal(false);
  keyword = '';
  page = 0;

  private readonly keywordChanged = new Subject<string>();

  constructor(private customerApi: CustomerApiService) {
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
    this.customerApi.search(this.keyword, this.page).subscribe({
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

  deactivate(customer: Customer): void {
    if (!confirm(`Deactivate ${customer.customerName}?`)) return;
    this.customerApi.deactivate(customer.id).subscribe(() => this.load());
  }

  reactivate(customer: Customer): void {
    this.customerApi.reactivate(customer.id).subscribe(() => this.load());
  }
}
