const SRC = 'https://checkout.razorpay.com/v1/checkout.js';

/** Loads Razorpay's official checkout script once. Rejects when blocked/offline so the UI can show a message. */
export function loadRazorpay() {
  if (window.Razorpay) return Promise.resolve(window.Razorpay);
  return new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = SRC;
    s.async = true;
    s.onload = () => (window.Razorpay ? resolve(window.Razorpay) : reject(new Error('Razorpay unavailable')));
    s.onerror = () => reject(new Error('Could not load the payment window. Check your connection or ad-blocker.'));
    document.body.appendChild(s);
  });
}

/**
 * Opens checkout for a server-created Razorpay order.
 * onPaid receives the three values the SERVER must verify; the browser never decides an order is paid.
 */
export async function openCheckout(p, { onPaid, onFailed, onDismiss }) {
  const Razorpay = await loadRazorpay();
  const rz = new Razorpay({
    key: p.keyId,
    amount: p.amount,
    currency: p.currency,
    name: p.name,
    order_id: p.razorpayOrderId,
    prefill: p.prefill,
    theme: { color: '#14213d' },
    handler: (r) => onPaid({ razorpayOrderId: r.razorpay_order_id, razorpayPaymentId: r.razorpay_payment_id, razorpaySignature: r.razorpay_signature }),
    modal: { ondismiss: () => onDismiss && onDismiss() },
  });
  rz.on('payment.failed', (r) => onFailed && onFailed(r?.error?.description || 'Payment failed'));
  rz.open();
}
