"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import {
  getStoredAdminKey,
  getStoredToken,
  setStoredAdminKey,
  setStoredToken,
} from "@/components/vocabulary/VocabClient";

const STUDENT_LINKS = [
  { href: "/learn/vocabulary", label: "Dashboard" },
  { href: "/learn/vocabulary/daily", label: "Daily Words" },
  { href: "/learn/vocabulary/categories", label: "Categories" },
  { href: "/learn/vocabulary/flashcards", label: "Flashcards" },
  { href: "/learn/vocabulary/quiz", label: "Quiz" },
  { href: "/learn/vocabulary/speaking", label: "Speaking" },
  { href: "/learn/vocabulary/review", label: "Review" },
  { href: "/learn/vocabulary/progress", label: "Progress" },
];

export default function VocabShell({ children, isAdminPage = false, onAuthChanged }) {
  const pathname = usePathname();
  const [hasAuth, setHasAuth] = useState(false);
  const [showAuthModal, setShowAuthModal] = useState(false);
  const [tokenInput, setTokenInput] = useState("");
  const [adminKeyInput, setAdminKeyInput] = useState("");

  useEffect(() => {
    const t = getStoredToken();
    const k = getStoredAdminKey();
    setTokenInput(t);
    setAdminKeyInput(k);
    setHasAuth(Boolean(t || (isAdminPage && k)));
  }, [isAdminPage]);

  function handleSaveAuth(e) {
    e.preventDefault();
    setStoredToken(tokenInput);
    setStoredAdminKey(adminKeyInput);
    setHasAuth(Boolean(tokenInput.trim() || (isAdminPage && adminKeyInput.trim())));
    setShowAuthModal(false);
    if (onAuthChanged) onAuthChanged();
    else window.location.reload();
  }

  function handleSignOut() {
    setStoredToken("");
    setStoredAdminKey("");
    setTokenInput("");
    setAdminKeyInput("");
    setHasAuth(false);
    if (onAuthChanged) onAuthChanged();
    else window.location.reload();
  }

  return (
    <div className="v-root">
      <header className="v-topbar">
        <div className="v-topbar-inner">
          <Link href="/learn/vocabulary" className="v-brand">
            <span className="v-brand-badge">SM</span>
            <span>SparkMate Vocabulary</span>
          </Link>

          <nav className="v-nav" aria-label="Vocabulary Navigation">
            {STUDENT_LINKS.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className={`v-nav-link ${pathname === item.href ? "active" : ""}`}
              >
                {item.label}
              </Link>
            ))}
            <Link
              href="/admin/vocabulary"
              className={`v-nav-link ${pathname === "/admin/vocabulary" ? "active" : ""}`}
            >
              Admin
            </Link>
          </nav>

          <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
            <button
              type="button"
              className="v-btn v-btn-outline"
              onClick={() => setShowAuthModal(true)}
            >
              {hasAuth ? "Session Connected" : "Sign In / Token"}
            </button>
            {hasAuth && (
              <button type="button" className="v-btn v-btn-outline" onClick={handleSignOut}>
                Sign Out
              </button>
            )}
          </div>
        </div>
      </header>

      <main className="v-main">
        {!hasAuth && !showAuthModal ? (
          <div className="v-card" style={{ maxWidth: 540, margin: "40px auto", textAlign: "center" }}>
            <h1 style={{ marginTop: 0, fontSize: 22 }}>Connect Your SparkMate Session</h1>
            <p style={{ color: "var(--v-text-muted)", lineHeight: 1.6 }}>
              Vocabulary APIs require a verified Firebase ID token{isAdminPage ? " or Admin Key" : ""} so
              your personal progress, daily goal, streak, and reviews sync securely to MongoDB.
            </p>
            <button
              type="button"
              className="v-btn v-btn-primary"
              onClick={() => setShowAuthModal(true)}
            >
              Connect Session
            </button>
          </div>
        ) : (
          children
        )}
      </main>

      {showAuthModal && (
        <div
          style={{
            position: "fixed",
            inset: 0,
            background: "rgba(15, 23, 42, 0.45)",
            display: "grid",
            placeItems: "center",
            padding: 16,
            zIndex: 50,
          }}
        >
          <form
            onSubmit={handleSaveAuth}
            className="v-card"
            style={{ width: "100%", maxWidth: 480, display: "grid", gap: 14 }}
          >
            <h2 style={{ margin: 0, fontSize: 20 }}>Session Authentication</h2>
            <p style={{ margin: 0, fontSize: 13, color: "var(--v-text-muted)" }}>
              Paste a valid Firebase ID token (for student &amp; admin routes)
              {isAdminPage ? " or your server ADMIN_KEY for admin management." : "."}
            </p>

            <label style={{ display: "grid", gap: 6, fontSize: 13, fontWeight: 600 }}>
              Firebase ID Token (Bearer)
              <textarea
                className="v-textarea"
                rows={3}
                value={tokenInput}
                onChange={(e) => setTokenInput(e.target.value)}
                placeholder="eyJhbGciOiJSUzI1NiIs..."
              />
            </label>

            {isAdminPage && (
              <label style={{ display: "grid", gap: 6, fontSize: 13, fontWeight: 600 }}>
                Admin Key (x-admin-key, optional if Firebase account is admin)
                <input
                  type="password"
                  className="v-input"
                  value={adminKeyInput}
                  onChange={(e) => setAdminKeyInput(e.target.value)}
                  placeholder="Enter ADMIN_KEY"
                />
              </label>
            )}

            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button
                type="button"
                className="v-btn v-btn-outline"
                onClick={() => setShowAuthModal(false)}
              >
                Cancel
              </button>
              <button type="submit" className="v-btn v-btn-primary">
                Save &amp; Continue
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
