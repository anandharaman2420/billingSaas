import { Component } from '@angular/core';

@Component({
  selector: 'app-forbidden',
  standalone: true,
  template: `
    <div style="max-width: 480px; margin: 80px auto; text-align: center;" class="card">
      <h1>403 - Forbidden</h1>
      <p>You don't have permission to access this page.</p>
    </div>
  `,
})
export class ForbiddenComponent {}
