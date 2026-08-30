// import { CommonModule } from '@angular/common';
// import { HttpErrorResponse } from '@angular/common/http';
// import { Component, signal } from '@angular/core';
// import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
// import { Router, RouterLink } from '@angular/router';
// import { AuthService } from '../../../core/auth/auth.service';
// import { ApiError } from '../../../core/models/auth.model';

// @Component({
//   selector: 'app-login',
//   standalone: true,
//   imports: [CommonModule, ReactiveFormsModule, RouterLink],
//   templateUrl: './login.component.html',
// })
// export class LoginComponent {
//   readonly loading = signal(false);
//   readonly errorMessage = signal<string | null>(null);

//   readonly form = this.fb.group({
//     email: ['', [Validators.required, Validators.email]],
//     password: ['', [Validators.required]],
//   });

//   constructor(
//     private fb: FormBuilder,
//     private authService: AuthService,
//     private router: Router,
//   ) {}

//   submit(): void {
//     if (this.form.invalid) {
//       this.form.markAllAsTouched();
//       return;
//     }

//     this.loading.set(true);
//     this.errorMessage.set(null);

//     const { email, password } = this.form.getRawValue();

//     this.authService.login({ email: email!, password: password! }).subscribe({
//       next: () => this.router.navigate(['/dashboard']),
//       error: (err: HttpErrorResponse) => {
//         const apiError = err.error as ApiError | undefined;
//         this.errorMessage.set(apiError?.message ?? 'Login failed. Please try again.');
//         this.loading.set(false);
//       },
//     });
//   }
// }

import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/models/auth.model';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
})
export class LoginComponent {
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
  ) {
    this.form = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required]],
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const { email, password } = this.form.getRawValue();

    this.authService.login({ email: email!, password: password! }).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: (err: HttpErrorResponse) => {
        const apiError = err.error as ApiError | undefined;
        this.errorMessage.set(apiError?.message ?? 'Login failed. Please try again.');
        this.loading.set(false);
      },
    });
  }
}
