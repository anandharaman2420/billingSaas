// import { CommonModule } from '@angular/common';
// import { HttpErrorResponse } from '@angular/common/http';
// import { Component, signal } from '@angular/core';
// import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
// import { Router, RouterLink } from '@angular/router';
// import { AuthService } from '../../../core/auth/auth.service';
// import { ApiError } from '../../../core/models/auth.model';

// @Component({
//   selector: 'app-register',
//   standalone: true,
//   imports: [CommonModule, ReactiveFormsModule, RouterLink],
//   templateUrl: './register.component.html',
// })
// export class RegisterComponent {
//   readonly loading = signal(false);
//   readonly errorMessage = signal<string | null>(null);

//   readonly form = this.fb.group({
//     businessName: ['', [Validators.required, Validators.maxLength(200)]],
//     ownerName: ['', [Validators.required, Validators.maxLength(150)]],
//     email: ['', [Validators.required, Validators.email]],
//     phone: ['', [Validators.required, Validators.pattern(/^[0-9+\-\s]{7,20}$/)]],
//     password: ['', [Validators.required, Validators.minLength(8)]],
//     city: [''],
//     state: [''],
//     gstin: [''],
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

//     this.authService.register(this.form.getRawValue() as any).subscribe({
//       next: () => this.router.navigate(['/dashboard']),
//       error: (err: HttpErrorResponse) => {
//         const apiError = err.error as ApiError | undefined;
//         this.errorMessage.set(apiError?.message ?? 'Registration failed. Please check your details.');
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
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './register.component.html',
})
export class RegisterComponent {
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
  ) {
    this.form = this.fb.group({
      businessName: ['', [Validators.required, Validators.maxLength(200)]],
      ownerName: ['', [Validators.required, Validators.maxLength(150)]],
      email: ['', [Validators.required, Validators.email]],
      phone: ['', [Validators.required, Validators.pattern(/^[0-9+\-\s]{7,20}$/)]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      city: [''],
      state: [''],
      gstin: [''],
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.authService.register(this.form.getRawValue() as any).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: (err: HttpErrorResponse) => {
        const apiError = err.error as ApiError | undefined;
        this.errorMessage.set(
          apiError?.message ?? 'Registration failed. Please check your details.'
        );
        this.loading.set(false);
      },
    });
  }
}
