import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { AppShellComponent } from './layout/app-shell.component';

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
    path: '',
    component: AppShellComponent,
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },

      // --- Customers: view/create/edit open to all roles (STAFF needs
      // this to bill a walk-in customer); deactivate is gated server-side. ---
      {
        path: 'customers',
        loadComponent: () =>
          import('./features/customers/list/customer-list.component').then((m) => m.CustomerListComponent),
      },
      {
        path: 'customers/new',
        loadComponent: () =>
          import('./features/customers/form/customer-form.component').then((m) => m.CustomerFormComponent),
      },
      {
        path: 'customers/:id',
        loadComponent: () =>
          import('./features/customers/form/customer-form.component').then((m) => m.CustomerFormComponent),
      },

      // --- Products: everyone can view (needed to build an invoice);
      // create/edit restricted to OWNER/ADMIN/MANAGER, enforced by the
      // roleGuard here for UX and independently by the backend's
      // @PreAuthorize, which is the actual security boundary. ---
      {
        path: 'products',
        loadComponent: () =>
          import('./features/products/list/product-list.component').then((m) => m.ProductListComponent),
      },
      {
        path: 'products/new',
        canActivate: [roleGuard('OWNER', 'ADMIN', 'MANAGER')],
        loadComponent: () =>
          import('./features/products/form/product-form.component').then((m) => m.ProductFormComponent),
      },
      {
        path: 'products/:id',
        canActivate: [roleGuard('OWNER', 'ADMIN', 'MANAGER')],
        loadComponent: () =>
          import('./features/products/form/product-form.component').then((m) => m.ProductFormComponent),
      },

      // --- Invoices: all roles view/create/edit-while-draft (STAFF's
      // core job is billing); issue and cancel are gated server-side
      // via @PreAuthorize, and the roleGuard here just avoids showing
      // buttons a STAFF user's click would get rejected. ---
      {
        path: 'invoices',
        loadComponent: () =>
          import('./features/invoices/list/invoice-list.component').then((m) => m.InvoiceListComponent),
      },
      {
        path: 'invoices/new',
        loadComponent: () =>
          import('./features/invoices/form/invoice-form.component').then((m) => m.InvoiceFormComponent),
      },
      {
        path: 'invoices/:id',
        loadComponent: () =>
          import('./features/invoices/detail/invoice-detail.component').then((m) => m.InvoiceDetailComponent),
      },
      {
        path: 'invoices/:id/edit',
        loadComponent: () =>
          import('./features/invoices/form/invoice-form.component').then((m) => m.InvoiceFormComponent),
      },

      // --- Services: same role policy as products. ---
      {
        path: 'services',
        loadComponent: () =>
          import('./features/services/list/service-list.component').then((m) => m.ServiceListComponent),
      },
      {
        path: 'services/new',
        canActivate: [roleGuard('OWNER', 'ADMIN', 'MANAGER')],
        loadComponent: () =>
          import('./features/services/form/service-form.component').then((m) => m.ServiceFormComponent),
      },
      {
        path: 'services/:id',
        canActivate: [roleGuard('OWNER', 'ADMIN', 'MANAGER')],
        loadComponent: () =>
          import('./features/services/form/service-form.component').then((m) => m.ServiceFormComponent),
      },

      // Future phases plug in here, e.g.:
      // { path: 'invoices', loadChildren: () => import('./features/invoices/invoices.routes') },
      // { path: 'payments',  loadChildren: () => import('./features/payments/payments.routes') },
      // { path: 'settings',  canActivate: [roleGuard('OWNER','ADMIN')], loadChildren: () => ... },

      {
        path: 'forbidden',
        loadComponent: () =>
          import('./shared/components/forbidden/forbidden.component').then((m) => m.ForbiddenComponent),
      },
    ],
  },

  { path: '**', redirectTo: 'dashboard' },
];
