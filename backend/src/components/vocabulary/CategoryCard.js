"use client";

import Link from "next/link";

export default function CategoryCard({ category, subcategories = [], onSelect }) {
  return (
    <div className="v-card" style={{ display: "grid", gap: 10 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 8 }}>
        <div>
          <h3 style={{ margin: 0, fontSize: 17 }}>{category.name}</h3>
          {category.description && (
            <p style={{ margin: "4px 0 0", fontSize: 13, color: "var(--v-text-muted)" }}>
              {category.description}
            </p>
          )}
        </div>
        <span className="v-pill v-pill-blue">{category.wordCount ?? 0} words</span>
      </div>

      {typeof category.learnedCount === "number" && (
        <div style={{ fontSize: 13, color: "var(--v-text-muted)" }}>
          Learned: <strong>{category.learnedCount}</strong> / {category.wordCount ?? 0}
        </div>
      )}

      {subcategories.length > 0 && (
        <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
          {subcategories.map((sub) => (
            <span key={sub.id} className="v-pill">
              {sub.name} ({sub.wordCount ?? 0})
            </span>
          ))}
        </div>
      )}

      <div style={{ display: "flex", gap: 8, marginTop: 4 }}>
        {onSelect ? (
          <button type="button" className="v-btn v-btn-outline" onClick={() => onSelect(category.id)}>
            Browse Words
          </button>
        ) : (
          <Link href={`/learn/vocabulary/categories?categoryId=${category.id}`} className="v-btn v-btn-outline">
            Explore
          </Link>
        )}
        <Link href={`/learn/vocabulary/daily?categoryId=${category.id}`} className="v-btn v-btn-primary">
          Learn Today
        </Link>
      </div>
    </div>
  );
}
