"use client";

import { useEffect, useState } from "react";
import { playWordAudio } from "@/components/vocabulary/VocabClient";

export default function Flashcard({ word, index = 0, total = 1, busy = false, onIKnow, onDontKnow }) {
  const [flipped, setFlipped] = useState(false);

  useEffect(() => {
    setFlipped(false);
  }, [word?.id]);

  if (!word) return null;

  return (
    <div style={{ maxWidth: 680, margin: "0 auto", display: "grid", gap: 18 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <span className="v-pill v-pill-blue">
          Card {index + 1} of {total}
        </span>
        <span className="v-pill">{word.level}</span>
      </div>

      <div
        className="v-flip-scene"
        role="button"
        tabIndex={0}
        onClick={() => setFlipped((f) => !f)}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            setFlipped((f) => !f);
          }
        }}
        aria-label="Flip flashcard"
      >
        <div className={`v-flip-card ${flipped ? "flipped" : ""}`}>
          {/* FRONT */}
          <div className="v-flip-face">
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <span className="v-pill">Front — Tap card to flip</span>
              <button
                type="button"
                className="v-btn v-btn-outline"
                onClick={(e) => {
                  e.stopPropagation();
                  playWordAudio(word.word, word.audioUrl);
                }}
              >
                🔊 Listen
              </button>
            </div>

            <div style={{ textAlign: "center", padding: "24px 0" }}>
              {word.imageUrl && (
                <img
                  src={word.imageUrl}
                  alt={word.word}
                  style={{
                    width: 140,
                    height: 100,
                    objectFit: "cover",
                    borderRadius: 12,
                    margin: "0 auto 16px",
                    display: "block",
                  }}
                />
              )}
              <div style={{ fontSize: 40, fontWeight: 800, letterSpacing: "-0.02em" }}>
                {word.word}
              </div>
              {word.pronunciation && (
                <div style={{ fontSize: 16, color: "var(--v-text-muted)", marginTop: 8 }}>
                  {word.pronunciation}
                </div>
              )}
            </div>

            <div style={{ textAlign: "center", fontSize: 13, color: "var(--v-text-muted)" }}>
              Click anywhere on the card to reveal Bengali meaning &amp; examples
            </div>
          </div>

          {/* BACK */}
          <div className="v-flip-face v-flip-back">
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <span className="v-pill v-pill-green">{word.partOfSpeech}</span>
              <span className="v-pill">Back — Tap to flip</span>
            </div>

            <div style={{ display: "grid", gap: 12, margin: "12px 0" }}>
              <div style={{ fontSize: 28, fontWeight: 800, color: "var(--v-primary)" }}>
                {word.meaning}
              </div>

              {(word.examples || []).slice(0, 2).map((ex, idx) => (
                <div
                  key={idx}
                  style={{
                    padding: 10,
                    borderRadius: 10,
                    background: "var(--v-bg)",
                    border: "1px solid var(--v-border)",
                    fontSize: 14,
                  }}
                >
                  <div style={{ fontWeight: 600 }}>{ex.en}</div>
                  <div style={{ color: "var(--v-text-muted)", marginTop: 2 }}>{ex.bn}</div>
                </div>
              ))}

              <div style={{ fontSize: 13, color: "var(--v-text-muted)" }}>
                {word.synonyms?.length > 0 && (
                  <div>
                    <strong>Synonyms:</strong> {word.synonyms.join(", ")}
                  </div>
                )}
                {word.antonyms?.length > 0 && (
                  <div style={{ marginTop: 4 }}>
                    <strong>Antonyms:</strong> {word.antonyms.join(", ")}
                  </div>
                )}
              </div>
            </div>

            <div style={{ fontSize: 12, color: "var(--v-text-muted)", textAlign: "center" }}>
              Rate your recall below to schedule your next spaced review
            </div>
          </div>
        </div>
      </div>

      <div style={{ display: "flex", gap: 12, justifyContent: "center" }}>
        <button
          type="button"
          className="v-btn v-btn-danger"
          style={{ flex: 1, maxWidth: 220 }}
          disabled={busy}
          onClick={() => onDontKnow && onDontKnow(word.id)}
        >
          Don&apos;t Know
        </button>
        <button
          type="button"
          className="v-btn v-btn-success"
          style={{ flex: 1, maxWidth: 220 }}
          disabled={busy}
          onClick={() => onIKnow && onIKnow(word.id)}
        >
          I Know ✓
        </button>
      </div>
    </div>
  );
}
