# KeepExact — SPEC.md

## One-Sentence Concept
KeepExact checks whether an AI-edited image changed only what the user asked to change, and flags unintended changes before the image is used or published.

## Target User And Use Scenario
People using AI image editing for creator work, product images, posters, character art, social posts, or repeated revisions. After an AI edit, the user uploads the original image and edited image, pastes the exact edit instruction, and checks whether anything unintended changed.

## Core User Path
1. Upload original image.
2. Upload edited image.
3. Paste the exact edit instruction.
4. Click “Check edit”.
5. Receive PASS / REVIEW / FAIL, compliance score, requested-change status, unintended-change findings, and a repair prompt.
6. Copy the repair prompt if needed.

## Platform & Technical Constraints
- Mobile-friendly web app.
- OpenAI API is server-side only; never expose API key to browser.
- Responses API with two image inputs and structured JSON output.
- Default model: gpt-5.6-luna.
- JPG/PNG/WEBP, max 10 MB each.
- Stateless Phase 1; KeepExact itself does not intentionally persist uploaded images.

## Phase 1 Scope
Included: image upload + preview, exact instruction input, validation, AI comparison, PASS/REVIEW/FAIL, score, findings, repair prompt, copy button, loading/errors, mobile/desktop, health endpoint.

Not included: accounts, payments, saved history, batch checking, video, image generation/repair, annotation, team features.

## Phase 2 — Monetization
Only after live AI verification passes: connect Stripe, add a paid credit package (initial hypothesis: US$9 / 30 checks), durable credit ledger, minimal identity layer, and real checkout verification.

## Explicitly Out Of Scope
Do not turn KeepExact into an image generator, editor, social network, generic AI chat app, or full creative suite before the verification loop is proven.

## Scenario Acceptance Tests
- First open clearly explains the product and three required inputs.
- Valid images + instruction produce structured result without page reload.
- Missing input shows a specific actionable message.
- Unsupported/oversized image is rejected safely.
- API failure is recoverable without losing selected images.
- Findings separate requested vs unintended changes; repair prompt is copyable.
- Mobile viewport requires no horizontal scrolling.

## Completion Rule
Phase 1 is complete only when local tests pass and the app is deployed. Live AI verification cannot be marked passed without a real OPENAI_API_KEY call. Monetization cannot be marked complete until a real payment checkout is connected and tested.
