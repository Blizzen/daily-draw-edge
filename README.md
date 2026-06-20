# Daily Draw Edge

Android companion for Yahoo's **Daily Draw** card game. Scans the 6 cards you're
dealt for a single game, computes each card's expected points
(`displayed points × P(yes)`), and recommends the best 4 to pick.

Each Daily Draw is tied to **one match**: 6 cards, each a **yes/no prop** (team-
or player-level) worth some points, with optional 1.25× / 1.5× boosts. You pick 4;
the set locks at that game's first pitch / kickoff. Score = points for the props
that hit. Optimal play = pick the 4 cards maximizing expected points — this app
finds them.

## Status

Design converged via a grilling session. **MLB-first.** Spike (#1) **passed**
against a live slate. **Core logic + a buildable Android app both done.** The full
chain (record → OCR → parse → odds → de-vig → EV rank) is wired end-to-end; the
APK builds. On-device capture/OCR is pending real-hardware verification.

### Modules

- **`:core`** — pure Kotlin/JVM, no Android deps. The whole brain, unit-tested.
  - `model/` — `Card`, `StatKey`, `Game`, `ProbEstimate`, `RankedCard`, `HandResult`
  - `odds/DeVig` — american→prob, two-way de-vig, one-sided haircut
  - `odds/OddsApi` — typed v4 client (`HttpURLConnection`; key passed per-call, never stored)
  - `odds/ProbabilityEstimator` — card + odds → `ProbEstimate`
  - `parse/PropParser` — OCR text → `Card`; `parse/MatchupResolver` — header → `Game`
  - `rank/Ranker` — EV, rank, recommend 4, no-data separation
  - `Cli` — runnable smoke test
- **`:app`** — Android (Kotlin + Compose), depends on `:core`.
  - `MainActivity` + Compose UI (API-key field, record/stop, results)
  - `capture/CaptureService` — MediaProjection → MediaRecorder mp4 (foreground)
  - `capture/FrameExtractor` — samples frames; `capture/CardOcr` — ML Kit OCR + parse + dedupe
  - `CaptureViewModel` — orchestrates extract → OCR → odds → rank

### Build / run

```
./gradlew :core:test                            # 21 pure-logic unit tests (no network)
./gradlew :core:run --args="<oddsApiKey>"       # live: ranks the demo NYM@PHI hand
./gradlew :app:assembleDebug                    # builds app/build/outputs/apk/debug/app-debug.apk
```

Requires an Android SDK; set its path in `local.properties` (`sdk.dir=...`, gitignored).

## Spike result (live, 2026-06-20 NYM@PHI)

Ran real Odds API calls against the exact cards. Pipeline works end-to-end.

| Card (real) | pts | P(yes) | EV | source |
|---|---|---|---|---|
| Mets 4+ runs | 5.25 | 43.7% | 2.29 | 2-way de-vig (BetMGM O+115/U-150) |
| Crawford 3+ H+R+RBI | 6.75 | 26.0% | 1.75 | raw 1-sided (DK O+285), vig-inflated |
| Alvarez HR | 5.50 | 15.4% | 0.85 | raw 1-sided (BetRivers O+550), vig-inflated |
| Phillies run in 1st inning | 5.00 | — | — | NO DATA (no per-team 1st-inning market) |

Findings:
- **All 4 prop families have live markets** (alt team totals, batter HR, batter
  H+R+RBI, 1st-inning) across DK/FanDuel/BetMGM/BetRivers.
- **Threshold lines often land on *alternate* markets posted ONE-SIDED (Over
  only)** → can't two-way de-vig; use raw implied + vig haircut or consensus.
- **Per-team 1st-inning run = genuine gap** → "no data → manual" (as designed).
- **Free tier ample** — whole test cost ~4 credits of 500/mo.

## v1 scope — MLB, market-driven

For MLB, mainstream odds APIs cover the card prop types well, so v1 is essentially
an odds-lookup pipeline with **no stats model**:

```
record carousel (MediaProjection)
  → sample frames @ ~5fps, ML Kit OCR, drop blur/partials, dedupe → 6 cards
  → parse each card → (subject, stat, comparator, line)
  → read header → identify the single game (team abbrevs + time are text)
  → look up de-vigged book odds for each prop  → P(yes)
  → EV = points × P(yes); rank 6, recommend top 4
  → cards with no market → 'no data', surfaced for manual judgment
```

No stats model, no lineup feed, no `StatsProvider`, no logging in v1.

## Card anatomy (from real screenshots)

- **Header** (MLB): `NYM 34-41 · TODAY 6:15 PM · PHI 40-35` (text — easy match
  resolution). `Max` = sum of your 4 selected cards' points.
- **Big point value** (e.g. `5.25 Points`) + optional boost subtitle
  (`3.5 POINTS · 1.5X MULTIPLIER`). Displayed points already include the boost.
- **Two subject types:**
  - **Team card** — team logo + record pill (`Mets · 34-41`); team-level prop.
  - **Player card** — photo + `Name · POS · #num` pill; player prop.
- **Prop line** — always a yes/no threshold.
- Card color = rarity/boost. Carousel: one card at a time; soccer screens look
  identical minus the text header (flags instead of team abbrevs).

### Observed MLB prop types
| Prop text | Normalized | Likely market |
|-----------|-----------|---------------|
| WILL SCORE 4+ RUNS (team) | team runs ≥ 4 | team total runs O3.5 |
| WILL SCORE A RUN IN THE 1ST INNING (team) | 1st-inning run = yes | 1st-inning team total O0.5 |
| WILL HIT A HOME RUN (player) | HR ≥ 1 | anytime HR |
| WILL RECORD 3+ COMBINED HITS + RUNS + RBIS (player) | H+R+RBI ≥ 3 | H+R+RBI prop |

## Decisions (locked)

| Area | Decision |
|------|----------|
| **First sport** | **MLB-first** end-to-end; soccer/others later |
| **Card input** | Screen recording via in-app **MediaProjection** |
| **Frame extraction** | Sample ~5fps → ML Kit OCR → drop low-confidence → dedupe by subject+prop → 6 cards |
| **OCR** | **ML Kit on-device** |
| **Prop parsing** | **Regex grammar + stat dictionary**; detect team-vs-player subject from the pill; unknown stat noun → flag for manual |
| **Entity/match** | Read text header to identify the single game; team cards → team, player cards → that game's roster |
| **Probability** | **Market-only for MLB v1** — book odds. Two-way standard lines → multiplicative de-vig. **One-sided alternate lines → raw implied with vig haircut / multi-book consensus** (spike finding). No model |
| **Missing odds** | Mark **'no data'**; rank the rest; if <4 rankable, prompt manual fill from unknowns |
| **Output** | EV-rank all 6, recommend top 4; raw points + % shown; no-data cards surfaced for manual |
| **Reroll** | Passive re-rank (re-scan after reroll) |
| **Outlier scope** | Relative EV only in v1 (absolute mispricing-curve detection needs logging — deferred) |
| **Logging** | None in v1 |
| **Tech stack** | Native **Kotlin + Jetpack Compose** |
| **Architecture** | Decide after the spike (market-only likely fits all-on-device) |

## Deferred (post-v1)

- **Soccer / other sports** via per-sport `StatsProvider` interface.
- **Stats model** — Poisson from per-90 rate; sample = recency-weighted blend of
  club + international; **start-prob weighted** `P(yes)=P(plays)×P(yes|plays)`
  from confirmed lineups. (Needed where odds are thin — i.e. soccer.)
- **Outlier / pricing-curve detection** via logging `(points, %, sport, prop)`.
- **Reroll optimizer** (optimal keep/reroll once an EV distribution is learned).

## Game mechanics reference

- One Daily Draw per game; all 6 cards from that game; locks at game start.
- **Reroll once:** rerolls only unselected (bottom) cards; kept cards safe; can
  swap after reroll before finalizing.
- Sports seen: majors + soccer. Currently MLB and FIFA World Cup.

## Target device

Galaxy S24+ (SM-S926U), One UI 8.5, Android 16.
