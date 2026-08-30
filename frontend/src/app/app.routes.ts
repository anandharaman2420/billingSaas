import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },

  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register/register.component').then((m) => m.RegisterComponent),
  },

  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
  },

  // Future phases plug in here as lazy-loaded feature routes, e.g.:
  // { path: 'customers', canActivate: [authGuard], loadChildren: () => import('./features/customers/customers.routes') },
  // { path: 'invoices',  canActivate: [authGuard], loadChildren: () => import('./features/invoices/invoices.routes') },

  {
    path: 'forbidden',
    loadComponent: () =>
      import('./shared/components/forbidden/forbidden.component').then((m) => m.ForbiddenComponent),
  },

  { path: '**', redirectTo: 'dashboard' },
];
