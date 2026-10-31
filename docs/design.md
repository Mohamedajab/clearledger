# Control plane design

Audience: someone inspecting account transfers and journal entries. Prioritise readable
amounts and a clear path to the underlying rows.

- Type: locally served Public Sans, 14px body, 12px supporting text; tabular money.
- Palette: ink `#20313b`, muted `#596b76`, navy `#243f59`, canvas `#f3f5f7`,
  white `#ffffff`, balanced/posted status `#286448`.
- Layout: pale navigation rail, shared balance strip, compact journal check,
  full-width payment rows. Give the money more prominence than its containers.
- Keep forms and tables legible on mobile; payment actions keep visible labels.
- Remove repeated pulse icons, tiny uppercase labels and unused CSS. Match dialog
  styles to the actual form markup and preserve visible keyboard focus.

References: [Anthropic frontend-design](https://github.com/anthropics/skills/tree/main/skills/frontend-design)
and [Vercel web-design-guidelines](https://github.com/vercel-labs/agent-skills/tree/main/skills/web-design-guidelines).
The layout follows payment operations rather than a portfolio landing-page pattern.
