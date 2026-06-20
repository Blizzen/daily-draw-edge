# Daily Draw Edge

Android companion for Yahoo's **Daily Draw** card game. Scans the 6 cards you're
dealt, computes each card's expected points (`displayed points × P(yes)`), and
recommends the best 4 to pick.

The game deals 6 cards, each a **yes/no player prop** (e.g. "WILL RECORD 2+
TACKLES", "WILL HAVE A SHOT ON TARGET") worth some points, with optional 1.25× /
1.5× boosts. You pick 4. Score = points for the props that hit. The optimal play
is to pick the 4 cards maximizing expected points — this app finds them, and
surfaces the "outlier" cards where the points are generous relative to the true
probability.

## Status

Pre-build. Design locked via a grilling session; feasibility spike next.

## Design decisions (locked)

| Area | Decision |
|------|----------|
| **Card input** | Screen recording → in-app **MediaProjection** capture; app decodes the clip, finds the 6 settled (stationary) frames, OCRs each, dedupes by player+prop |
| **Card anatomy** | matchup flags · big point value · optional boost subtitle (`3 POINTS · 1.5X MULTIPLIER`) · player photo · name pill (`Name · POS · #num` + country) · prop line. All props are yes/no thresholds |
| **Odds source** | Spike first: try an odds **API**, then test **scraping**, to determine plausibility for the actual prop types |
| **Missing odds** | **Stats-model fallback** — estimate P(yes) from player per-90 stats (Poisson/empirical). Likely the *primary* path for niche soccer shot/tackle props |
| **Stats source** | **Per-sport mix** behind a common `StatsProvider` interface (e.g. FBref soccer, MLB stats API for baseball) |
| **Availability** | **Start-prob weighted**: `P(yes) = P(plays enough) × P(yes \| plays)`. Needs predicted-lineup / recent-starts data per player |
| **Output** | **EV-rank all 6, recommend the top 4.** Raw points + implied % shown underneath |
| **Reroll** | Passive **re-rank only** in v1 (you decide what to reroll, re-scan, it re-ranks). Optimal keep/reroll advisor deferred |
| **Logging** | **None in v1** |
| **OCR** | **ML Kit on-device** text recognition (free, offline; card text is large clean rendered type) |
| **Tech stack** | **Native Kotlin + Jetpack Compose** (first-class MediaProjection / MediaCodec / ML Kit access) |
| **Architecture** | **Decide after the spike** — feasibility (esp. whether scraping needs a server IP) dictates on-device vs phone+backend |

## Game mechanics reference

- 6 cards dealt, shown one at a time in a carousel; move up to 4 to the top slots.
- **Reroll once:** rerolls only the *unselected* (bottom) cards; kept cards are
  safe. Can swap/rearrange after the reroll before finalizing.
- Boost multipliers (1.25× / 1.5×) shown as a subtitle; card color indicates
  rarity/boost.
- Sports seen: all majors + soccer. Currently MLB and FIFA World Cup.

## Open design questions (next grilling branches)

- Settled-frame detection heuristic (frame-diff threshold, dwell detection)
- Player/entity resolution (OCR name + POS + #num + country → stats source ID)
- Predicted-lineup / start-probability data source per sport
- Probability model details (sample window, de-vig when book odds exist)
- Spike plan: which odds API, scrape targets, success criteria
- min SDK (target device: Galaxy S24+, One UI 8.5, Android 16)

## Target device

Galaxy S24+ (SM-S926U), One UI 8.5, Android 16.
