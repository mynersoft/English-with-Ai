"use client";

import { playWordAudio } from "@/components/vocabulary/VocabClient";

const QUALITY_OPTIONS = [
  { quality: 1, label: "Again (Reset)", cls: "v-btn-danger" },
  { quality: 3, label: "Hard", cls: "v-btn-outline" },
  { quality: 4, label: "Good", cls: "v-btn-primary" },
  { quality: 5, label: "Easy", cls: "v-btn-success" },
];

export default function ReviewCard({ item, allowRating = false, busy = false, onRate }) {
  if (!item) return null;
  const p = item.progress || {};

  return (
    <div className="v-card" style={{ display: "grid", gap: 12 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", flexWrap: "wrap", gap: 8 }}>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <h3 style={{ margin: 0, fontSize: 22 }}>{item.word}</h3>
            <button
              type="button"
              className="v-btn v-btn-outline"
              style={{ minHeight: 32, padding: "4px 10px" }}
              onClick={() => playWordAudio(item.word, item.audioUrl)}
            >
              🔊
            </button>
          </div>
          {item.pronunciation && (
            <div style={{ fontSize: 13, color: "var(--v-text-muted)" }}>{item.pronunciation}</div>
          )}
          <div style={{ fontSize: 16, fontWeight: 700, color: "var(--v-primary)", marginTop: 6 }}>
            {item.meaning}
          </div>
        </div>

        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          <span className="v-pill">{item.level}</span>
          <span className={`v-pill ${p.status === "mastered" ? "v-pill-green" : "v-pill-amber"}`}>
            {p.status}
          </span>
          <span className="v-pill">Interval: {p.intervalDays ?? 0}d</span>
          <span className="v-pill">EF: {p.easeFactor ?? 2.5}</span>
        </div>
      </div>

      {item.examples?.[0] && (
        <div style={{ fontSize: 14, color: "var(--v-text-muted)" }}>
          &ldquo;{item.examples[0].en}&rdquo; — {item.examples[0].bn}
        </div>
      )}

      {allowRating && onRate && (
        <div style={{ display: "flex", flexWrap: "wrap", gap: 8, paddingTop: 8, borderTop: "1px solid var(--v-border)" }}>
          {QUALITY_OPTIONS.map((opt) => (
            <button
              key={opt.quality}
              type="button"
              className={`v-btn ${opt.cls}`}
              disabled={busy}
              onClick={() => onRate(item.id, opt.quality)}
            >
              {opt.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
