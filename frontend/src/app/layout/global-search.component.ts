import { CommonModule } from '@angular/common';
import { Component, ElementRef, HostListener, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { debounceTime, distinctUntilChanged, Subject, switchMap } from 'rxjs';
import { SearchApiService } from '../core/services/search-api.service';
import { GlobalSearchResponse } from '../core/models/search.model';

@Component({
  selector: 'app-global-search',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './global-search.component.html',
})
export class GlobalSearchComponent {
  query = '';
  readonly results = signal<GlobalSearchResponse | null>(null);
  readonly open = signal(false);

  private readonly queryChanged = new Subject<string>();

  constructor(
    private searchApi: SearchApiService,
    private router: Router,
    private elementRef: ElementRef,
  ) {
    this.queryChanged
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((q) => (q.trim().length >= 2 ? this.searchApi.search(q) : [])),
      )
      .subscribe((res) => {
        if (res) {
          this.results.set(res as GlobalSearchResponse);
          this.open.set(true);
        } else {
          this.results.set(null);
          this.open.set(false);
        }
      });
  }

  onInput(): void {
    this.queryChanged.next(this.query);
  }

  hasAnyResults(): boolean {
    const r = this.results();
    return !!r && (r.customers.length + r.products.length + r.services.length + r.invoices.length) > 0;
  }

  goTo(path: string[]): void {
    this.open.set(false);
    this.query = '';
    this.router.navigate(path);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.open.set(false);
    }
  }
}
