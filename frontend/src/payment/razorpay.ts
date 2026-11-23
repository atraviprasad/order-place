// Razorpay Checkout (sandbox keys only). The script comes from Razorpay, the card form runs on their domain,
// so card details never touch this app or our servers.

interface RazorpaySuccess {
  razorpay_payment_id: string;
  razorpay_order_id: string;
  razorpay_signature: string;
}

interface RazorpayFailure {
  error: { code?: string; description?: string; reason?: string };
}

interface RazorpayOptions {
  key: string;
  order_id: string;
  amount: number; // in the smallest unit (paise)
  currency: string;
  name: string;
  description: string;
  prefill?: { name?: string; email?: string };
  theme?: { color: string };
  handler: (response: RazorpaySuccess) => void;
  modal?: { ondismiss?: () => void };
}

interface RazorpayInstance {
  open(): void;
  on(event: 'payment.failed', callback: (response: RazorpayFailure) => void): void;
}

declare global {
  interface Window {
    Razorpay?: new (options: RazorpayOptions) => RazorpayInstance;
  }
}

export type { RazorpayFailure, RazorpayInstance, RazorpayOptions, RazorpaySuccess };

const SCRIPT_URL = 'https://checkout.razorpay.com/v1/checkout.js';
let loading: Promise<NonNullable<Window['Razorpay']>> | null = null;

export function loadRazorpay(): Promise<NonNullable<Window['Razorpay']>> {
  if (window.Razorpay) return Promise.resolve(window.Razorpay);
  if (loading === null) {
    loading = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = SCRIPT_URL;
      script.async = true;
      script.onload = () => (window.Razorpay ? resolve(window.Razorpay) : reject(new Error('Razorpay did not load')));
      script.onerror = () => {
        loading = null; // allow a retry
        reject(new Error('Could not load the payment window. Check your internet connection or ad blocker.'));
      };
      document.head.appendChild(script);
    });
  }
  return loading;
}

/** The development gateway (payments.gateway=fake) makes order ids that start with this. There is no payment window for it. */
export function isFakeGatewayOrder(gatewayOrderId: string): boolean {
  return gatewayOrderId.startsWith('order_fake_');
}
