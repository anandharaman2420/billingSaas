import { Injectable } from '@angular/core';

interface RazorpayPaymentResponse {
  razorpay_payment_id: string;
  razorpay_order_id: string;
  razorpay_signature: string;
}

interface RazorpayOptions {
  key: string;
  amount: number;
  currency: string;
  name: string;
  description: string;
  order_id: string;
  handler: (response: RazorpayPaymentResponse) => void;
  modal: { ondismiss: () => void };
}

interface RazorpayInstance {
  open(): void;
}

declare global {
  interface Window {
    Razorpay?: new (options: RazorpayOptions) => RazorpayInstance;
  }
}

@Injectable({ providedIn: 'root' })
export class RazorpayCheckoutService {
  private scriptPromise?: Promise<void>;

  async open(options: RazorpayOptions): Promise<void> {
    await this.loadScript();
    if (!window.Razorpay) {
      throw new Error('Razorpay Checkout failed to initialize.');
    }
    new window.Razorpay(options).open();
  }

  private loadScript(): Promise<void> {
    if (window.Razorpay) return Promise.resolve();
    if (this.scriptPromise) return this.scriptPromise;

    this.scriptPromise = new Promise<void>((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'https://checkout.razorpay.com/v1/checkout.js';
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => {
        this.scriptPromise = undefined;
        reject(new Error('Unable to load Razorpay Checkout. Check your network and try again.'));
      };
      document.head.appendChild(script);
    });
    return this.scriptPromise;
  }
}
