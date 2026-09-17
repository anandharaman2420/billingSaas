import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';
import { ExpenseApiService } from '../../../core/services/expense-api.service';
import { Expense } from '../../../core/models/expense.model';
import { PageResponse } from '../../../core/models/common.model';

@Component({
  selector: 'app-expense-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './expense-list.component.html',
})
export class ExpenseListComponent implements OnInit {
  readonly result = signal<PageResponse<Expense> | null>(null);
  readonly loading = signal(false);
  keyword = '';
  page = 0;

  private readonly keywordChanged = new Subject<string>();

  constructor(private expenseApi: ExpenseApiService) {
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
    this.expenseApi.search(this.keyword, this.page).subscribe({
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

  remove(expense: Expense): void {
    if (!confirm(`Delete the expense "${expense.description}"? This cannot be undone.`)) return;
    this.expenseApi.delete(expense.id).subscribe(() => this.load());
  }
}
