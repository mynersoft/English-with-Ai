"use client";

import { useEffect, useState } from "react";
import { playWordAudio } from "@/components/vocabulary/VocabClient";

export default function VocabularyCard({
  word,
  index = 0,
  total = 1,
  showBengaliMeaningDefault = true,
  autoPronunciation = false,
  onPrev,
  onNext,
  onToggleBookmark,
  onIKnow,
  onDontKnow,
  busy = false,
}) {
  const [revealed, setRevealed] = useState(showBengaliMeaningDefault);
  const isFavorite = Boolean(word?.progress?.isFavorite);

  useEffect(() => {
    setRevealed(showBengaliMeaningDefault);
    if (autoPronunciation && word?.word) {
      playWordAudio(word.word, word.audioUrl);
    }
  }, [word?.id, showBengaliMeaningDefault, autoPronunciation, word?.word, word?.audioUrl]);

  if (!word) return null;

  return (
    <div className="v-card" style={{ maxWidth: 760, margin: "0 auto", display: "grid", gap: 18 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 8 }}>
        <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
          <span className="v-pill v-pill-blue">
            Word {index + 1} / {total}
          </span>
          <span className="v-pill">{word.level}</span>
          <span className="v-pill">{word.partOfSpeech}</span>
          {word.category?.name && <span className="v-pill">{word.category.name}</span>}
          {word.progress?.status && (
            <span className={`v-pill ${word.progress.status === "mastered" ? "v-pill-green" : "v-pill-amber"}`}>
              {word.progress.status}
            </span>
          )}
        </div>

        <button
          type="button"
          className="v-btn v-btn-outline"
          disabled={busy}
          onClick={() => onToggleBookmark && onToggleBookmark(word.id)}
          aria-label="Bookmark word"
        >
          {isFavorite ? "★ Bookmarked" : "☆ Bookmark"}
        </button>
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 16, flexWrap: "wrap" }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 34, fontWeight: 800, letterSpacing: "-0.02em" }}>
            {word.word}
          </h2>
          {word.pronunciation && (
            <div style={{ color: "var(--v-text-muted)", fontSize: 16, marginTop: 4 }}>
              {word.pronunciation}
            </div>
          )}
        </div>

        <div style={{ display: "flex", gap: 10 }}>
          <button
            type="button"
            className="v-btn v-btn-primary"
            onClick={() => playWordAudio(word.word, word.audioUrl)}
          >
            🔊 Listen Audio
          </button>
          <button
            type="button"
            className="v-btn v-btn-outline"
            onClick={() => setRevealed((v) => !v)}
          >
            {revealed ? "Hide Meaning" : "Reveal Meaning"}
          </button>
        </div>
      </div>

      {word.imageUrl && (
        <div style={{ borderRadius: 12, overflow: "hidden", maxHeight: 220, background: "var(--v-surface-muted)" }}>
          <img
            src={word.imageUrl}
            alt={word.word}
            style={{ width: "100%", maxHeight: 220, objectFit: "cover", display: "block" }}
          />
        </div>
      )}

      {revealed ? (
        <div className="v-alert v-alert-info" style={{ fontSize: 18, fontWeight: 700 }}>
          Meaning: {word.meaning}
        </div>
      ) : (
        <div className="v-alert" style={{ background: "var(--v-surface-muted)", color: "var(--v-text-muted)" }}>
          Meaning hidden — click &ldquo;Reveal Meaning&rdquo; to check Bengali translation.
        </div>
      )}

      <div>
        <h3 style={{ margin: "0 0 10px", fontSize: 15, color: "var(--v-text-muted)" }}>
          Example Sentences
        </h3>
        <div style={{ display: "grid", gap: 10 }}>
          {(word.examples || []).map((ex, idx) => (
            <div
              key={idx}
              style={{
                padding: 12,
                borderRadius: 12,
                background: "var(--v-bg)",
                border: "1px solid var(--v-border)",
              }}
            >
              <div style={{ fontWeight: 600, fontSize: 15 }}>
                {idx + 1}. {ex.en}
              </div>
              {revealed && (
                <div style={{ color: "var(--v-text-muted)", marginTop: 4, fontSize: 14 }}>
                  {ex.bn}
                </div>
              )}
            </div>
          ))}
        </div>
      </div>

      {(word.synonyms?.length > 0 || word.antonyms?.length > 0) && (
        <div className="v-grid v-grid-2">
          {word.synonyms?.length > 0 && (
            <div style={{ padding: 12, borderRadius: 12, background: "var(--v-surface-muted)" }}>
              <strong style={{ fontSize: 13 }}>Synonyms:</strong>{" "}
              <span style={{ fontSize: 14 }}>{word.synonyms.join(", ")}</span>
            </div>
          )}
          {word.antonyms?.length > 0 && (
            <div style={{ padding: 12, borderRadius: 12, background: "var(--v-surface-muted)" }}>
              <strong style={{ fontSize: 13 }}>Antonyms:</strong>{" "}
              <span style={{ fontSize: 14 }}>{word.antonyms.join(", ")}</span>
            </div>
          )}
        </div>
      )}

      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          flexWrap: "wrap",
          gap: 10,
          paddingTop: 8,
          borderTop: "1px solid var(--v-border)",
        }}
      >
        <div style={{ display: "flex", gap: 8 }}>
          <button type="button" className="v-btn v-btn-outline" disabled={!onPrev || index === 0} onClick={onPrev}>
            ← Previous Word
          </button>
          <button
            type="button"
            className="v-btn v-btn-outline"
            disabled={!onNext || index >= total - 1}
            onClick={onNext}
          >
            Next Word →
          </button>
        </div>

        <div style={{ display: "flex", gap: 10 }}>
          <button
            type="button"
            className="v-btn v-btn-danger"
            disabled={busy}
            onClick={() => onDontKnow && onDontKnow(word.id)}
          >
            Don&apos;t Know
          </button>
          <button
            type="button"
            className="v-btn v-btn-success"
            disabled={busy}
            onClick={() => onIKnow && onIKnow(word.id)}
          >
            I Know ✓
          </button>
        </div>
      </div>
    </div>
  );
}
