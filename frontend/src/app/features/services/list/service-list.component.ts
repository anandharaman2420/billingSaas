import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';
import { ServiceApiService } from '../../../core/services/service-api.service';
import { BillableService } from '../../../core/models/service.model';
import { PageResponse } from '../../../core/models/common.model';

@Component({
  selector: 'app-service-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './service-list.component.html',
})
export class ServiceListComponent implements OnInit {
  readonly result = signal<PageResponse<BillableService> | null>(null);
  readonly loading = signal(false);
  keyword = '';
  page = 0;

  private readonly keywordChanged = new Subject<string>();

  constructor(private serviceApi: ServiceApiService) {
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
    this.serviceApi.search(this.keyword, this.page).subscribe({
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

  deactivate(service: BillableService): void {
    if (!confirm(`Deactivate ${service.serviceName}?`)) return;
    this.serviceApi.deactivate(service.id).subscribe(() => this.load());
  }

  reactivate(service: BillableService): void {
    this.serviceApi.reactivate(service.id).subscribe(() => this.load());
  }
}
