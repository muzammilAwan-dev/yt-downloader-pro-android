# 15. UI/UX resources and screenshot-inspired design

Checked 15 Nov 2026 by the `general` research agent at the owner's explicit request. Research only: no skills installed,
scripts executed, dependencies upgraded, or UI implemented. No evidence establishes a universally "best" design skill.

## Recommended foundation for ClipHarbor

Native Android, Jetpack Compose and Material 3 take precedence over web-oriented instructions.

| Resource / skill | Verified purpose | Limitations |
|---|---|---|
| [Google: Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) | Semantic color, typography, shapes, components, dynamic color and theming | Official implementation reference, not an AI skill or a complete product-design process. Check stability before adopting Expressive APIs. |
| [Google: adaptive Compose skill](https://github.com/android/skills/blob/main/jetpack-compose/adaptive/SKILL.md), [adaptive guide](https://developer.android.com/develop/ui/compose/layouts/adaptive/get-started-with-adaptive-apps) | Adaptive navigation/panes, resizing, state preservation, keyboard/mouse, foldables and screenshot verification | Strong provenance for native implementation. The reviewed instructions assume Navigation 3 and include experimental APIs; do not upgrade the app or apply the skill wholesale. Not a visual-identity designer. |
| [Google: Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility/key-steps), [Android design accessibility](https://developer.android.com/design/ui/mobile/guides/foundations/accessibility) | Semantics, TalkBack, scalable content, traversal, contrast and at least 48dp touch targets | Mandatory review baseline. Material components and screenshots alone cannot establish accessibility. |
| [Community: material-design-3-ui](https://github.com/skydashnet/material-design-3-ui-skill/blob/main/SKILL.md), [MIT license](https://github.com/skydashnet/material-design-3-ui-skill/blob/main/LICENSE) | Goals → information architecture → adaptive structure → tokens → components/states → accessibility → handoff | Unofficial, cross-platform but Compose-aware. A planning candidate, not a verified Compose API expert or independently proven usability improvement. |
| [Anthropic: frontend-design](https://github.com/anthropics/skills/blob/main/skills/frontend-design/SKILL.md), [Apache-2.0 license](https://github.com/anthropics/skills/blob/main/skills/frontend-design/LICENSE.txt) | Art direction, typography, tokens, wireframes, motion, copy and screenshot critique | Web-oriented, including CSS guidance. Optional critique aid only; not a substitute for Android navigation, notification or TalkBack expertise. |

Also investigated: [charliesbot/android-design](https://github.com/charliesbot/android-design). Native Compose design
judgment is relevant, but its reviewed preview workflow depends on an Apple-Silicon previewer and describes an unsigned
app/quarantine removal. Not the first choice for this Windows workspace.

Google's [Android Skills methodology](https://developer.android.com/blog/posts/inside-android-skills-built-for-deprecation)
describes evaluation-backed official skills and cautions against indiscriminate collections and web-specific engineering
instructions. Before any future installation, inspect all instructions, references, scripts, licenses, permissions and
dependency assumptions; pin the reviewed revision. The links above are mutable and were not pinned or comprehensively
security-audited. Do not run remote-script-to-shell installers blindly.

## Screenshots to request

Send 8–12 original-resolution screenshots where available, numbered in flow order:

1. Home / paste-link entry and Android share flow.
2. Link analysis/loading and resulting media details.
3. Format picker (video/audio tabs and expanded options).
4. Queue (waiting and active items).
5. Error/retry state and completed-file actions.
6. Settings and relevant notifications.
7. Light/dark variants of home and picker, if available.

Keep system bars visible, but redact accounts, personal notifications, private links, tokens and signed URL queries.
Mark unavailable states instead of recreating them. For each screenshot, note what you like, dislike and want inspiration
from. Include phone/Android version, dimensions, language, font/display scale and preferred density/theme. Existing
ClipHarbor colors/icon, intended audience, tablet scope and preferred queue actions also help.

## Original design workflow (proposed, not yet implemented)

**Screenshot status (15 Nov 2026):** all nine original 720×1640 images from the owner's first archive were visually
reviewed. Image/flow observations are in `16-BROWSER-AUTH-AND-MEDIA-DISCOVERY.md` §1; no competitor images/assets or personal
feed details committed. The owner subsequently supplied a second archive path that is not currently present at that
location; the second batch has not been reviewed. Website-login/browser behaviour also has separate owner-reported runtime
evidence and REA research in doc 16; screenshots alone do not establish renderer implementation or session transfer.

- Extract interaction principles from the owner's screenshots; do not trace layouts or reuse competitor code, assets,
  branding or identity. Snaptube is a reference, not the ClipHarbor design system.
- Map Phase 1's share/paste → picker → durable queue → saved-file journey. Leave ads, discovery feeds and unrelated browser
  features out.
- Propose two original wireframe directions for review, then define independent ClipHarbor tokens and a Material
  component/state map. Handle empty/loading/error/recovery, saving, cancellation, notification denial and local diagnostics.
- Verify rendered Compose across themes/window sizes, large text, TalkBack, keyboard focus and real-device flows.
  Research is not proof that any design or implementation passes these checks.
