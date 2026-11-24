"use client";

const TOKEN_STORAGE_KEY = "sparkmate_vocab_token";
const ADMIN_KEY_STORAGE_KEY = "sparkmate_vocab_admin_key";

export function getStoredToken() {
  if (typeof window === "undefined") return "";
  return window.localStorage.getItem(TOKEN_STORAGE_KEY) || "";
}

export function setStoredToken(token) {
  if (typeof window === "undefined") return;
  if (token) window.localStorage.setItem(TOKEN_STORAGE_KEY, token.trim());
  else window.localStorage.removeItem(TOKEN_STORAGE_KEY);
}

export function getStoredAdminKey() {
  if (typeof window === "undefined") return "";
  return window.localStorage.getItem(ADMIN_KEY_STORAGE_KEY) || "";
}

export function setStoredAdminKey(key) {
  if (typeof window === "undefined") return;
  if (key) window.localStorage.setItem(ADMIN_KEY_STORAGE_KEY, key.trim());
  else window.localStorage.removeItem(ADMIN_KEY_STORAGE_KEY);
}

export async function apiFetch(path, { method = "GET", body, admin = false } = {}) {
  const headers = { Accept: "application/json" };
  const token = getStoredToken();
  const adminKey = getStoredAdminKey();

  if (token) headers.Authorization = `Bearer ${token}`;
  if (admin && adminKey) headers["x-admin-key"] = adminKey;
  if (body !== undefined) headers["Content-Type"] = "application/json";

  const res = await fetch(path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
    cache: "no-store",
  });

  const text = await res.text();
  let json = {};
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    json = { error: { message: text || `Server error (${res.status})` } };
  }

  if (!res.ok || json.success === false) {
    const errMsg =
      json?.error?.message ||
      (typeof json?.error === "string" ? json.error : "") ||
      `Request failed (${res.status})`;
    const error = new Error(errMsg);
    error.status = res.status;
    error.code = json?.error?.code || "API_ERROR";
    throw error;
  }

  return json.data !== undefined ? json.data : json;
}

/**
 * Plays word MP3 audioUrl when configured, or uses browser speechSynthesis if available.
 */
export function playWordAudio(wordText, audioUrl) {
  if (typeof window === "undefined") return false;
  if (audioUrl) {
    const audio = new Audio(audioUrl);
    audio.play().catch(() => {});
    return true;
  }
  if (wordText && "speechSynthesis" in window) {
    try {
      window.speechSynthesis.cancel();
      const utter = new SpeechSynthesisUtterance(wordText);
      utter.lang = "en-US";
      utter.rate = 0.9;
      window.speechSynthesis.speak(utter);
      return true;
    } catch {
      return false;
    }
  }
  return false;
}
