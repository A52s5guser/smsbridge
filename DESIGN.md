# SMSBridge — Apple Messages × Ant × Liquid Glass Design System

## Context and Goals
Transform SMSBridge into an iOS 26 Liquid Glass messaging app that feels tactile and native on Android. The source of truth is Ant Design tokens (primary #1677FF) rendered as Apple Messages: translucent blurred surfaces, floating pill composer, iMessage bubble shape with tail, initials avatars, and full haptic+tactile feedback. Must run smoothly on S25 Plus and A52s (minSdk 33) with graceful fallback.

Goals: (1) Messages app recognizability, (2) Liquid Glass blur everywhere without lag, (3) Every tap feels tactile, (4) Settings consolidates pairing/role/config.

## Design Tokens and Foundations

### Color — Ant base + Glass + Apple bubbles
- **Primary** #1677FF (Ant) — sent bubbles, CTAs, active tab indicator
- **Secondary** #8B5CF6 — header gradient end, accents
- **Success** #16A34A, **Warning** #D97706, **Danger** #DC2626
- **Text** #111827 (light) / #E9ECF4 (dark), **Text secondary** #565E73 / #9AA4B8
- **Surface** #FFFFFF (light) / #151B27 (dark), **Background** #F6F8FC / #0C1018
- **Bubble sent** #1677FF (Ant primary, not #0A84FF) with white text, **Bubble received** #E9E9EB (light) / #2C333E (dark)
- **Glass light** `glass_top` #F4FEFFFF / `glass_bottom` #E8FFFFFF, border #1F16203A, sheen #38FFFFFF
- **Glass dark** `glass_top` #E6202A3D / `glass_bottom` #D4151C2B, border #33FFFFFF
- Opacity for Liquid Glass: 60% (as specified), blur radius 20px (RenderEffect)

### Typography — Ant + SF Pro blend
- **Family** Plus Jakarta Sans (Ant) — weights 400/500/600/700; fallback sans-serif matches SF Pro metrics. Mono JetBrains Mono for numbers.
- **Scale** 12/14/16/20/24/32. Chats: title 20 bold, subtitle 13. Bubbles: 15. Tab: 10 caps or 13 bold. Time stamps: 11.
- Line-height 1.35 for bubbles, 1.2 for titles.

### Spacing — Ant 4/8/12/16/24/32
- Card padding 16, list row gap 8, bubble padding 12/14, pill search 12, tab bar 8.

### Shape — Apple 20-24px override
- **Cards/sheets** 24px, **Bubbles** 18px (sent tail bottom-right, received tail bottom-left), **Pill search/composer** 24px (full pill 999px), **Avatars** circular 40dp, **Tabs** 12px.

### Blur & Glass
- **Header, bottom tab bar, floating composer, search pill** — `RenderEffect.createBlurEffect(20, 20, Shader.TileMode.CLAMP)` on container background (API 31+). Fallback: 60% opacity solid + border + sheen gradient.
- Elevation 3dp under glass to imply depth; border 1dp `glass_border`.

### Motion & Tactile
- Every button: `stateListAnimator` scale 0.97 on pressed + elevation 0→3dp, duration 90ms, spring interpolator (0.2, 0.8). 
- Haptic: `performHapticFeedback(HAPTIC_FEEDBACK_ENABLED)` + `CLOCK_TICK` on tap, `CONFIRM` on send/call. Must be optional (respects system haptic setting).
- Transitions: list→thread slide 220ms ease-out (translateX 16dp + alpha 0→1), bubbles stagger 30ms.

## Component-Level Rules

### 1. Top Header + Search
- Anatomy: large title "Messages" (24 bold) left, subtitle "SMSBridge • Linked" 12 secondary, right gear → Settings. Below: pill search (frosted, 48dp, icon + placeholder "Search").
- States: search focused → border primary 1.5dp + blur intensifies.
- Blur: header container is glass with 20px blur, not solid.

### 2. Bottom Glass Tab Bar
- 4 tabs: Chats (bubble), Contacts (person), Calls (phone), Settings (gear). Frosted glass pill bar, 64dp height, icons above label (10sp caps).
- Active: primary tint + filled icon, inactive: secondary text. Indicator: 3dp dot under active.
- Tactile: each tab has scale+haptic.

### 3. Chats List Row
- Anatomy: 40dp initials avatar (gradient primary→secondary, white initials) left, title 16 bold + time 11 secondary top row, subtitle 13 secondary single line, unread dot 8dp primary.
- Background: transparent row with `bg_row` 16px radius, pressed → glass highlight.
- Swipe: trailing swipe reveals Call (green) + Delete (danger).

### 4. Bubbles (Thread)
- Sent: Ant #1677FF, white text, 18px radius with 4px tail cut at bottom-right, align end, max 78% width.
- Received: #E9E9EB / dark #2C333E, primary text, tail bottom-left.
- Time inside bubble 10 secondary at bottom-right with 6dp padding.
- Grouping: consecutive from same sender collapse spacing to 2dp.

### 5. Floating Pill Composer
- Anatomy: frosted pill bar (56dp) with text field (expandable 1-4 lines) + circular send (primary, arrow icon) right. Attachment `+` left if needed.
- Blur 20px, border, shadow 6dp. Focused: border primary.
- Empty → send disabled (40% opacity), typing → send enabled + scale pop.

### 6. Contacts & Calls
- Contacts: same avatar row, trailing call (primary) + message (glass) icons.
- Calls: iPhone dialer grid (circular 72dp keys, 24sp number + 10sp letters), green call, red end, backspace. Recent calls list above dialer with call type icons (in #0A8A4C, out #2F47D6, missed #DC2626).

### 7. Settings
- Grouped sections: Connection (role, peer picker, status, Start/Stop), Notifications (sound/vibrate), Appearance (theme auto/light/dark, blur toggle), About (version, GitHub). Danger zone: Clear data.
- Rows: iOS-style grouped cards (24px), 48dp rows, chevron, switch (Ant switch with tactile).

## Accessibility
- WCAG 2.2 AA: text contrast ≥4.5:1, glass text has 60%+ opacity over blurred bg tested.
- Hit area ≥44dp, focus-visible ring 2dp primary.
- Keyboard: Tab navigates tabs→search→list→composer, Enter sends.
- Content descriptions for avatars, call icons.

## Content and Tone
- Concise, friendly, professional. Labels: "Messages", "Search", "Send", "Call". Empty states: "No conversations yet — start a chat" with illustration.
- Errors: "Bluetooth disconnected — check A52s" with retry.

## Anti-Patterns
- Don't use raw hex outside tokens.
- Don't place glass text below 4.5:1 contrast.
- Don't animate blur radius on scroll (performance).
- Don't mix Ant 8px and Apple 24px radii in same surface.
- Don't hide role toggle outside Settings.
- Don't make tactile scale >0.97 (feels janky).

## QA Checklist
- [ ] Light/Dark auto follows system, glass opacity 60% readable in both.
- [ ] Header, tab bar, composer have 20px blur on S25; fallback solid on A52s if GPU lag detected.
- [ ] Every button scales 0.97 + haptic tick on press (test on both devices).
- [ ] Bubbles are Ant #1677FF sent / #E9E9EB received with tails, max 78% width.
- [ ] Chats rows have 40dp initials avatars, correct time/subtitle.
- [ ] Search pill filters all three tabs.
- [ ] Settings contains Connection/Notifications/Appearance/About/Danger.
- [ ] Thread transition is slide 220ms, no jank.
- [ ] Hit areas ≥44dp, focus ring visible, screen reader labels present.

