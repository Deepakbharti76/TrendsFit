// Remembers the visitor's chat (id + secret token) in this browser so the conversation survives a refresh.
const KEY = 'tf_support_chat';

export function loadChat() {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) || 'null');
    if (v && Number.isInteger(v.id) && typeof v.token === 'string' && v.token.length >= 16) return { id: v.id, token: v.token };
  } catch { /* storage unavailable or corrupted */ }
  return null;
}

export function saveChat(chat) {
  try { localStorage.setItem(KEY, JSON.stringify({ id: chat.id, token: chat.token })); } catch { /* ignore */ }
}

export function clearChat() {
  try { localStorage.removeItem(KEY); } catch { /* ignore */ }
}

export function validateStart({ name, email, message }) {
  const e = {};
  if (!String(name || '').trim()) e.name = 'Please enter your name.';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(email || '').trim())) e.email = 'Please enter a valid email address.';
  if (!String(message || '').trim()) e.message = 'Please type a message.';
  else if (String(message).length > 2000) e.message = 'Messages can be up to 2000 characters.';
  return e;
}
