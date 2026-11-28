# QR Libre Android design tokens

The app is dark only: one palette, no light scheme and no dynamic colour. The
tokens below were sampled from the reference site's live stylesheet for a
lime-on-charcoal look. Only the design language is borrowed. No brand asset,
wordmark or copy comes from there.

## Colour

| Token | Hex | Role |
| --- | --- | --- |
| Page | `#0E0E0E` | `background`, bottom bar |
| Card | `#171717` | `surface`, capture cards, summary pill |
| Elevated | `#1E1E1E` | `surfaceVariant`, icon tiles, dialogs |
| Divider | `#262626` | `outlineVariant`, hairline rules |
| Lime | `#CFFF2E` | `primary`, accent, active navigation |
| On lime | `#0B0B0B` | `onPrimary`, text on lime |
| Green | `#00DF1A` | `secondary` |
| Warning | `#F6B60B` | `tertiary`, missing app selection |
| Danger | `#EB3131` | `error`, clear-history action |
| Text | `#F5F4F0` | `onBackground`, `onSurface` |
| Muted | `#A2A2A1` | `onSurfaceVariant`, secondary text |
| Switch off | `#3A3A3A` | off track and thumb border |

Capture bodies use lime at 80% alpha (`#CCCFFF2E`). Composited over `#171717`
that is about `#AAD129`, roughly 10:1 contrast, so the technical text stays
readable while reading as lime.

## Type

Geist Sans (Regular 400, Medium 500, Bold 700) and Geist Mono (Regular 400) are
bundled as Android font resources in `app/src/main/res/font/`, downloaded from
<https://github.com/vercel/geist-font>. The licence is SIL OFL 1.1; a copy is in
`android/licenses/Geist-OFL.txt`. No font provider dependency is used, so the
fonts ship in the APK.

- Display and headline styles are weight 700 with tight negative tracking.
- Display styles use tabular figures so capture counters do not jump.
- Titles use 500; body and captions use 400.
- Section labels are small and wide-tracked for the uppercase labels.
- Geist Mono is reserved for technical text such as capture bodies.

## Shape

- 24dp: cards, hero, capture cards, privacy cards.
- 20dp: medium surfaces.
- Pill (50%): buttons, chips, search field, summary pill.
- 12dp: icon tiles and app icons.

## Motion

- Tab changes: 180ms fade plus a short horizontal slide.
- History list: `animateContentSize` as captures arrive or clear.
- Compose follows the system animator duration scale, so transitions resolve
  immediately when animations are turned off in developer options.

## System bars

Light icons on the dark page. `enableEdgeToEdge` uses `SystemBarStyle.dark`
with the page colour as the scrim, and the window theme keeps light status and
navigation icons. Some OEM skins (MIUI, for example) paint an opaque black
status-bar window regardless; the icons stay light and the band is near-black.
