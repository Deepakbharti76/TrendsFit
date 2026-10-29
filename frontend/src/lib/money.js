/** Formats rupees for display, e.g. 1299 -> "₹1,299". Never used to compute totals; the server owns all prices. */
export function inr(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return '₹0';
  const hasPaise = Math.round(n * 100) % 100 !== 0;
  return '₹' + n.toLocaleString('en-IN', { minimumFractionDigits: hasPaise ? 2 : 0, maximumFractionDigits: 2 });
}
