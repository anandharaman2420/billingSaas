import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { AppShellComponent } from './layout/app-shell.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },

  {
    path: 'login',
    title: 'Sign In – BillingSaaS',
    loadComponent: () => import('./features/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    title: 'Register Business – BillingSaaS',
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
        title: 'Dashboard – BillingSaaS',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'pos',
        title: 'POS Billing Counter – BillingSaaS',
        loadComponent: () =>
          import('./features/pos/pos.component').then((m) => m.PosComponent),
      },

      // Customers
      {
        path: 'customers',
        title: 'Customers – BillingSaaS',
        loadComponent: () =>
          import('./features/customers/list/customer-list.component').then((m) => m.CustomerListComponent),
      },
      {
        path: 'customers/new',
        title: 'New Customer – BillingSaaS',
        loadComponent: () =>
          import('./features/customers/form/customer-form.component').then((m) => m.CustomerFormComponent),
      },
      {
        path: 'customers/:id',
        title: 'Edit Customer – BillingSaaS',
        loadComponent: () =>
          import('./features/customers/form/customer-form.component').then((m) => m.CustomerFormComponent),
      },

      // Products
      {
        path: 'products',
        title: 'Products – BillingSaaS',
        loadComponent: () =>
          import('./features/products/list/product-list.component').then((m) => m.ProductListComponent),
      },
      {
        path: 'products/new',
        title: 'New Product – BillingSaaS',
        canActivate: [roleGuard('OWNER', 'ADMIN', 'MANAGER')],
        loadComponent: () =>
          import('./features/products/form/product-form.component').then((m) => m.ProductFormComponent),
      },
      {
        path: 'products/:id',
        title: 'Edit Product – BillingSaaS',
        canActivate: [roleGuard('OWNER', 'ADMIN', 'MANAGER')],
        loadComponent: () =>
          import('./features/products/form/product-form.component').then((m) => m.ProductFormComponent),
      },

      // Invoices
      {
        path: 'invoices',
        title: 'Invoices – BillingSaaS',
        loadComponent: () =>
          import('./features/invoices/list/invoice-list.component').then((m) => m.InvoiceListComponent),
      },
      {
        path: 'invoices/new',
        title: 'New Invoice – BillingSaaS',
        loadComponent: () =>
          import('./features/invoices/form/invoice-form.component').then((m) => m.InvoiceFormComponent),
      },
      {
        path: 'invoices/:id',
        title: 'Invoice Details – BillingSaaS',
        loadComponent: () =>
          import('./features/invoices/detail/invoice-detail.component').then((m) => m.InvoiceDetailComponent),
      },
      {
        path: 'invoices/:id/edit',
        title: 'Edit Invoice – BillingSaaS',
        loadComponent: () =>
          import('./features/invoices/form/invoice-form.component').then((m) => m.InvoiceFormComponent),
      },

      // Services
      {
        path: 'services',
        title: 'Services – BillingSaaS',
        loadComponent: () =>
          import('./features/services/list/service-list.component').then((m) => m.ServiceListComponent),
      },
      {
        path: 'services/new',
        title: 'New Service – BillingSaaS',
        canActivate: [roleGuard('OWNER', 'ADMIN', 'MANAGER')],
        loadComponent: () =>
          import('./features/services/form/service-form.component').then((m) => m.ServiceFormComponent),
      },
      {
        path: 'services/:id',
        title: 'Edit Service – BillingSaaS',
        canActivate: [roleGuard('OWNER', 'ADMIN', 'MANAGER')],
        loadComponent: () =>
          import('./features/services/form/service-form.component').then((m) => m.ServiceFormComponent),
      },

      // Expenses
      {
        path: 'expenses',
        title: 'Expenses – BillingSaaS',
        canActivate: [roleGuard('OWNER', 'ADMIN', 'MANAGER')],
        loadComponent: () =>
          import('./features/expenses/list/expense-list.component').then((m) => m.ExpenseListComponent),
      },
      {
        path: 'expenses/new',
        title: 'New Expense – BillingSaaS',
        canActivate: [roleGuard('OWNER', 'ADMIN', 'MANAGER')],
        loadComponent: () =>
          import('./features/expenses/form/expense-form.component').then((m) => m.ExpenseFormComponent),
      },
      {
        path: 'expenses/:id',
        title: 'Edit Expense – BillingSaaS',
        canActivate: [roleGuard('OWNER', 'ADMIN', 'MANAGER')],
        loadComponent: () =>
          import('./features/expenses/form/expense-form.component').then((m) => m.ExpenseFormComponent),
      },

      // Reports
      {
        path: 'reports',
        title: 'Reports & Analytics – BillingSaaS',
        canActivate: [roleGuard('OWNER', 'ADMIN', 'MANAGER')],
        loadComponent: () =>
          import('./features/reports/reports.component').then((m) => m.ReportsComponent),
      },

      // Users
      {
        path: 'users',
        title: 'Team Members – BillingSaaS',
        canActivate: [roleGuard('OWNER', 'ADMIN')],
        loadComponent: () =>
          import('./features/users/list/user-list.component').then((m) => m.UserListComponent),
      },
      {
        path: 'users/new',
        title: 'New Team Member – BillingSaaS',
        canActivate: [roleGuard('OWNER', 'ADMIN')],
        loadComponent: () =>
          import('./features/users/form/user-form.component').then((m) => m.UserFormComponent),
      },
      {
        path: 'users/:id',
        title: 'Edit Team Member – BillingSaaS',
        canActivate: [roleGuard('OWNER', 'ADMIN')],
        loadComponent: () =>
          import('./features/users/form/user-form.component').then((m) => m.UserFormComponent),
      },

      // Settings
      {
        path: 'settings',
        title: 'Business Settings – BillingSaaS',
        canActivate: [roleGuard('OWNER', 'ADMIN', 'MANAGER')],
        loadComponent: () =>
          import('./features/settings/settings.component').then((m) => m.SettingsComponent),
      },

      // Profile
      {
        path: 'profile',
        title: 'My Profile – BillingSaaS',
        loadComponent: () =>
          import('./features/profile/profile.component').then((m) => m.ProfileComponent),
      },

      // Forbidden
      {
        path: 'forbidden',
        title: '403 Forbidden – BillingSaaS',
        loadComponent: () =>
          import('./shared/components/forbidden/forbidden.component').then((m) => m.ForbiddenComponent),
      },
    ],
  },

  { path: '**', redirectTo: 'dashboard' },
];
