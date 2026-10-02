import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { UserApiService } from '../../../core/services/user-api.service';
import { UserRole } from '../../../core/models/auth.model';
import { ApiError } from '../../../core/models/auth.model';

@Component({
  selector: 'app-user-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './user-form.component.html',
})
export class UserFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  userId: string | null = null;

  readonly roles: UserRole[] = ['OWNER', 'ADMIN', 'MANAGER', 'STAFF'];

  readonly form = this.fb.group({
    fullName: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    password: ['', [Validators.minLength(8)]],
    role: ['STAFF' as UserRole, Validators.required],
  });

  constructor(
    private userApi: UserApiService,
    private route: ActivatedRoute,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.userId = this.route.snapshot.paramMap.get('id');
    if (this.userId) {
      // Editing: password isn't changed here and email is fixed once created.
      this.form.get('password')?.clearValidators();
      this.form.get('email')?.disable();
      this.userApi.getById(this.userId).subscribe((user) => this.form.patchValue(user as any));
    } else {
      this.form.get('password')?.addValidators(Validators.required);
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const raw = this.form.getRawValue();
    const save$ = this.userId
      ? this.userApi.update(this.userId, { fullName: raw.fullName!, phone: raw.phone || undefined, role: raw.role! })
      : this.userApi.create({
          fullName: raw.fullName!,
          email: raw.email!,
          phone: raw.phone || undefined,
          password: raw.password!,
          role: raw.role!,
        });

    save$.subscribe({
      next: () => this.router.navigate(['/users']),
      error: (err: HttpErrorResponse) => {
        const apiError = err.error as ApiError | undefined;
        this.errorMessage.set(apiError?.message ?? 'Failed to save team member.');
        this.loading.set(false);
      },
    });
  }
}
