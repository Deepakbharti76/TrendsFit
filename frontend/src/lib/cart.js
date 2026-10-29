// Pure cart helpers (unit-tested). The cart only stores which variants and how many; prices come from the server quote.
export const MAX_QTY = 10;

export function addLine(lines, line) {
  const qty = Math.max(1, Math.min(MAX_QTY, line.qty || 1));
  const i = lines.findIndex((l) => l.variantId === line.variantId);
  if (i === -1) return [...lines, { ...line, qty }];
  const next = lines.slice();
  next[i] = { ...next[i], ...line, qty: Math.min(MAX_QTY, next[i].qty + qty) };
  return next;
}

export function setQty(lines, variantId, qty) {
  if (qty < 1) return removeLine(lines, variantId);
  return lines.map((l) => (l.variantId === variantId ? { ...l, qty: Math.min(MAX_QTY, qty) } : l));
}

export function removeLine(lines, variantId) {
  return lines.filter((l) => l.variantId !== variantId);
}

export function countItems(lines) {
  return lines.reduce((n, l) => n + l.qty, 0);
}

/** Keeps only well-formed lines when reading from localStorage. */
export function sanitize(raw) {
  if (!Array.isArray(raw)) return [];
  return raw
    .filter((l) => l && Number.isInteger(l.variantId) && Number.isInteger(l.qty) && l.qty > 0)
    .map((l) => ({ variantId: l.variantId, qty: Math.min(MAX_QTY, l.qty), slug: l.slug, name: l.name, size: l.size, color: l.color, image: l.image }));
}
