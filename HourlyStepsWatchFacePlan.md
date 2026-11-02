# Hourly Steps watch face — implementation plan

A Pixel Watch 4 (41 mm and 45 mm) watch face that puts the Hourly Steps app's two-ring progress
view around the time, in the app's own colour themes. Written 2026-11-02 as a brief for an
implementation session.

---

## 0. Context the implementer needs

**Hourly Steps** is a standalone Wear OS 6 (API 36) app, Kotlin + Compose, package `com.hourlysteps`.
It reminds the wearer at :50 when they are short of an hourly step goal derived from a daily goal
(default 7,000/day over 12 reminder hours = 584 steps/hour; intervals of 30 min, 1 h or 2 h are
possible). It already exposes its data to watch faces through two complication data sources, and it
has a tile whose two-ring design this watch face must mirror.

Source (read these first; do **not** edit them from the watch face project):

```
<hourly-steps-repo>/app/src/main/java/com/hourlysteps/domain/AppTheme.kt                  # the 7 themes, 4 colours each
<hourly-steps-repo>/app/src/main/java/com/hourlysteps/surfaces/SurfaceContent.kt           # ring + complication content rules
<hourly-steps-repo>/app/src/main/java/com/hourlysteps/surfaces/StepsComplicationServices.kt # the two data sources
<hourly-steps-repo>/app/src/main/java/com/hourlysteps/surfaces/HourlyStepsTileService.kt   # the two-ring tile layout (reference look)
<hourly-steps-repo>/app/src/main/java/com/hourlysteps/ui/Format.kt                         # number formatting ("3,100")
<hourly-steps-repo>/app/src/main/AndroidManifest.xml                                        # complication service declarations
<hourly-steps-repo>/demo/11-tile-two-rings.png, themes-home.png, 13-watch-face-complications.png
<hourly-steps-repo>/README.md, docs/HourlyStepsPlan.md, docs/V2Plan.md
```

`<hourly-steps-repo>` is currently the working folder of the Claude session titled
"Hourly Steps Wear OS app":
`C:\Users\devon\AppData\Local\Claude-Data\claude_pzs8sxrjxfjjc\scratch-workspaces\be2cfa67-6df5-48d1-85fa-ec1bc851e1d9\5781582e-c3fa-431c-bfe9-e19feddfadb9\scratch-2026-11-02-cfa340`.
That is a session-only folder: if that session is deleted the code goes with it, and the other
session is still actively editing there. Treat it as **read-only reference**. If Devon moves it to a
permanent folder, update the path above.

Hourly Steps build stack to match: AGP 8.13.2, Kotlin 2.3.0, compileSdk 36, minSdk 30 (app),
JDK 17 (Android Studio's JBR at `C:\Program Files\Android\Android Studio\jbr`), SDK at
`C:\Users\devon\AppData\Local\Android\Sdk` (platforms 34/36/36.1, system images android-34 and
android-36, emulator present). Existing AVDs: `Wear_OS_6_Large_Round`, `Pixel_8`.

Devon's global rule (CLAUDE.md): work on a branch named `feature_<short_name>`, and the project must
compile and pass all checks before each commit.

---

## 1. Goal and non-goals

**Goal.** A Watch Face Format (WFF) watch face, installable on both Pixel Watch 4 sizes, that shows:

- the time (digital), date,
- an **outer ring** = today's steps against the daily goal, turning to the theme's `goalMet` colour once met,
- an **inner ring** = this interval's steps against its target,
- the numbers behind both rings, in the app's wording ("412" / "of 584", "3,100 today"),
- the app's **seven colour themes** as a user-selectable style, Violet default,
- a battery-friendly **always-on (ambient)** variant,
- tapping the rings opens Hourly Steps.

Data comes from the app's existing complication data sources, so the face works wherever the app is
installed and degrades gracefully (system step count) where it isn't.

**Non-goals (v1).** No analog variant, no weather, no editing the Hourly Steps app itself, no Play
Store listing work (keep it Play-compatible, though). The face cannot read the app's theme setting
directly (see §6); syncing them automatically is out of scope.

---

## 2. Target devices

| Device | Display | Density | Wear OS | WFF |
|---|---|---|---|---|
| Pixel Watch 4, 41 mm | 408 × 408 px round AMOLED (confirm with `adb shell wm size` on the device) | 320 ppi | 6 (API 36) | v4 |
| Pixel Watch 4, 45 mm | 456 × 456 px round AMOLED (confirm likewise) | 320 ppi | 6 (API 36) | v4 |

WFF renders a single design canvas and scales it to the physical display, so **one `watchface.xml`
at 450 × 450 covers both sizes**. Density is the same on both, so the 45 mm just has more room; the
only size-specific concern is legibility of the smallest text on the 41 mm. Only add a
`watch_face_shapes.xml` with a second file if the small size genuinely needs different sizes after
testing (§10).

Emulator stand-ins: "Wear OS Large Round" (454 × 454) and "Wear OS Small Round" (384 × 384),
`system-images;android-36;android-wear-signed;x86_64`. The small one is a harsher test than the
real 41 mm, which is fine.

---

## 3. Key technical decisions

1. **Watch Face Format v4, resource-only APK.** Since January 2026 WFF is the only format Wear OS
   accepts for new watch faces, and Google requires the watch face bundle to be **separate from the
   app's bundle**. WFF v4 is the version that ships with Wear OS 6 / API 36 (v1→Wear OS 4/API 33,
   v2→5/34, v3→5.1/35, v4→6/36; a v5 exists for newer releases and is not needed). Manifest:
   `com.google.wear.watchface.format.version = 4`, `android:hasCode="false"`, `minSdk 36`.
2. **Data via the app's complications, not WFF's built-in `[STEP_COUNT]`.** The hourly number only
   exists inside Hourly Steps. Each ring is a `ComplicationSlot` of type `RANGED_VALUE` whose
   `DefaultProviderPolicy` names the app's data source as `primaryProvider`:
   - today ring → `com.hourlysteps/com.hourlysteps.surfaces.TodayComplicationService`, fallback
     `defaultSystemProvider="STEP_COUNT"` (`RANGED_VALUE`)
   - interval ring → `com.hourlysteps/com.hourlysteps.surfaces.SlotComplicationService`, fallback
     `EMPTY`
   The face renders the ring itself from `[COMPLICATION.RANGED_VALUE_VALUE]`, `_MIN`, `_MAX`, and
   the text from `[COMPLICATION.TEXT]` / `[COMPLICATION.TITLE]`. Both sources already publish
   `RANGED_VALUE, SHORT_TEXT, LONG_TEXT` with a 300 s update period and a tap action that opens
   `MainActivity`, and the app calls `requestUpdateAll()` after step batches (≤ once a minute) and
   after checks, so the rings are as live as the tile.
3. **Separate Git repo, separate Gradle project.** Name: `hourly-steps-watchface`, suggested
   location `C:\Users\devon\Documents\GitHub\hourly-steps-watchface` (Devon confirms the folder).
   Reasons: Google requires a separate bundle anyway, and the app repo is being edited by another
   session right now. Theme colours are duplicated into the face's XML; §6 has the parity rule.
4. **Vector-only artwork.** Rings are `PartDraw` arcs, text uses `SYNC_TO_DEVICE` (the system font,
   Google Sans on Pixel, which matches the tile). No bitmaps except the mandatory preview image.
   This keeps memory far under the Play limits (10 MB ambient / 100 MB active).

---

## 4. What the complications deliver (contract with the app)

From `SurfaceContent.kt` and `StepsComplicationServices.kt`, as of 2026-11-02:

| Source | RANGED_VALUE value / max | TEXT | TITLE | LONG_TEXT |
|---|---|---|---|---|
| Today (`TodayComplicationService`) | `min(todayTotal, goal)` / `goal` | `"3,100"` | `"today"` | `"3,100 of 7,000 today"` |
| Interval (`SlotComplicationService`) | `min(stepsInSlot, slotTarget)` / `slotTarget` | `"412"` | `"of 584"` | `"412 of 584 this hour"` |

States the face must handle, both sources:

| App state | value / max | TEXT | What the face should show |
|---|---|---|---|
| Active | real numbers | number | ring + number + title |
| Daily goal met | today value == max (value is clamped) | number | outer ring in `goalMet` colour; "Goal met" label replaces "today" |
| Paused | 0 / 1 | `"Paused"` | empty track, the word "Paused" where the number goes |
| Day off (or no daily goal) | 0 / 1 | `"Off"` | empty track, "Off" |
| Outside reminder window (interval source only) | 0 / 1 | `"--"` | inner ring empty, "--" |
| Hourly Steps not installed | fallback provider | system text | today ring from the system step counter, inner ring hidden |

Detection in WFF: "goal met" is
`[COMPLICATION.RANGED_VALUE_VALUE] >= [COMPLICATION.RANGED_VALUE_MAX]` **and** `MAX > 1` (the
unavailable states use 0/1). Preview data (shown in the watch face editor before real data arrives)
is `412 of 584` and `3,100 of 7,000`.

Thousands separators: `TEXT` already carries them ("3,100"). If you want "of 7,000" next to today's
number, format `RANGED_VALUE_MAX` yourself or omit the goal and let the ring carry the ratio;
don't print the raw max without separators next to a separated number.

Optional ask for the **app** session (not this project): put the interval wording into the
interval source's title ("of 584 this hour") so the face doesn't have to hard-code "this hour",
which is wrong when the interval is 30 min or 2 h. Until then the face shows "of 584" only.

---

## 5. Visual design (450 × 450 canvas, centre 225,225)

Dark face, black background, same hierarchy as the tile (`11-tile-two-rings.png`): rings at the
edge, big white number, coloured captions.

| Element | Position / size (canvas units) | Style |
|---|---|---|
| Background | full | `#000000` |
| **Outer ring (today)** | centre radius ≈ 214, stroke 14, round caps, starts at 12 o'clock, clockwise | track `theme.track`; progress `theme.today`, or `theme.goalMet` once met |
| **Inner ring (interval)** | centre radius ≈ 194, stroke 12, round caps, same start | track `theme.track`; progress `theme.slot` |
| Date | top, baseline y ≈ 82, size 22, letter-spaced caps | `#CAC4D0` ("TEXT_DIM" from `RingColors.kt`), e.g. `SUN 2` |
| **Time** | centred, cap height ≈ 110, baseline y ≈ 250 | white, `SYNC_TO_DEVICE` bold; 12 h shows no leading zero and a small `AM`/`PM` (size 20) to the right; 24 h shows `HH:MM` |
| Interval number | centred, size 44 bold, y ≈ 300 | white; `[COMPLICATION.TEXT]` of the interval slot |
| Interval caption | centred, size 20, y ≈ 330 | `theme.slot`; `[COMPLICATION.TITLE]` ("of 584") |
| Today line | centred, size 20, y ≈ 362 | `theme.today` (or `goalMet` when met); `"Today " + TEXT` or `"Goal met · " + TEXT` |
| Hint when inner slot is empty | same place as interval number, size 18 | dim; "Add Hourly Steps" (only when the slot's complication type is EMPTY) |

Tap targets: the two ring slots are the whole-face region (`BoundingOval` 450 × 450 for the outer,
~410 × 410 for the inner); tapping anywhere not covered by text opens Hourly Steps via the
complication's own tap action. That matches the tile, which opens the app on tap.

Margins: keep every text element inside a 390-unit circle so nothing clips on the round bezel at
408 px, and never below y ≈ 395 (the Pixel Watch bottom is where the system draws nothing, but the
round edge cuts text).

### Ambient (always-on) variant

Use `Variant mode="AMBIENT"` to:

- hide the interval number/caption, today line and date (alpha 0) or keep the date only;
- thin both rings to stroke 6 and dim their colours (≈ 55 % alpha) to cut lit pixels for OLED
  burn-in; keep the track very dark or hide it;
- keep the time, with weight NORMAL instead of bold, white at ≈ 70 %;
- nothing updates faster than once a minute (no seconds anywhere on this face).

WFF v4 supports ambient transitions; a 300 ms alpha fade is enough.

### Typography and tags (confirm exact names in the WFF data-source reference)

Time: `[HOUR_1_12_Z]`/`[HOUR_0_23_Z]`, `[MINUTE_Z]`, `[IS_24_HOUR_MODE]`, `[AMPM_STATE]` or the AM/PM
string tag. Date: `[DAY_OF_WEEK_S]` and `[DAY]`. Use `Template` + `Parameter` to compose.

---

## 6. User configuration

**Theme** — one `ColorConfiguration` (`id="themeColor"`) with seven `ColorOption`s, four colours
each, in the fixed order **today, slot, goalMet, track** so expressions are always
`themeColor.0 … themeColor.3`. Values copied from `AppTheme.kt` (ARGB with alpha dropped):

| Option (display name) | today | slot | goalMet | track |
|---|---|---|---|---|
| Violet (default) | `#E3D2FF` | `#A98BFF` | `#FFCC80` | `#2D2640` |
| Classic | `#D0BCFF` | `#4DD0E1` | `#FFCC80` | `#2E2A36` |
| Ocean | `#90CAF9` | `#2DD4BF` | `#FFE082` | `#1C2C36` |
| Forest | `#9CD6A0` | `#D4E157` | `#FFD54F` | `#22302A` |
| Sunset | `#FF9E80` | `#FFC44D` | `#FFF176` | `#36281F` |
| Rose | `#FFC2DC` | `#FF6FA8` | `#FFE082` | `#36252E` |
| Graphite | `#EEEEEE` | `#90A4AE` | `#FFD54F` | `#2A2C2E` |

Neutral text: white `#FFFFFF`, dim `#CAC4D0` (from `RingColors.kt`).

**Parity rule:** these seven rows must match `AppTheme.kt` exactly. Put the table in the face's
README and add a comment in `watchface.xml` pointing at `AppTheme.kt`. If the app adds a theme,
the face gets the same row.

**Flavors** — seven `Flavor`s, one per theme, so the companion app shows the themes in a row.

**Optional toggles** (`BooleanConfiguration`, only if cheap): "Show date". Nothing else in v1.

Note: a WFF face cannot read the app's DataStore, so the theme is chosen in the watch face editor.
Say so in the README.

---

## 7. Complication slots

| slotId | Name | supportedTypes | Bounding | Default policy | Renders |
|---|---|---|---|---|---|
| 1 | Today ring | `RANGED_VALUE SHORT_TEXT EMPTY` | `BoundingOval` 0,0,450,450 | primary `com.hourlysteps/com.hourlysteps.surfaces.TodayComplicationService` (`RANGED_VALUE`), fallback `STEP_COUNT` (`RANGED_VALUE`) | outer ring + today line |
| 2 | Interval ring | `RANGED_VALUE SHORT_TEXT EMPTY` | `BoundingOval` 20,20,410,410 | primary `com.hourlysteps/com.hourlysteps.surfaces.SlotComplicationService` (`RANGED_VALUE`), fallback `EMPTY` | inner ring + number + caption |

Both slots `isCustomizable="true"` so the wearer can swap sources, but with these defaults nothing
has to be configured after install. Give each a `displayName` string resource and a `ScreenReader`
element. Provide a `Complication type="SHORT_TEXT"` rendering too (number + title, no ring) in case
a user picks a text-only source. Hard limit is eight slots; we use two, leaving room later for
small corner slots (battery, heart rate) if Devon wants them.

Progress arc angle for a ring: `360 * clamp((value - min) / (max - min), 0, 1)`, drawn from
anchor 0° (12 o'clock) clockwise, matching the tile (`ARC_ANCHOR_START`, `STROKE_CAP_ROUND`).
Guard against `max == min` (the unavailable 0/1 state is fine, but write the expression so a 0/0
never divides).

---

## 8. Project layout and build

```
hourly-steps-watchface/
  settings.gradle.kts                 # rootProject "HourlyStepsWatchFace", include(":watchface")
  build.gradle.kts
  gradle/libs.versions.toml           # agp = "8.13.2" (same as the app)
  gradle/wrapper/...
  watchface/
    build.gradle.kts                  # com.android.application, enableKotlin = false,
                                      # namespace/applicationId com.hourlysteps.watchface,
                                      # compileSdk 36, minSdk 36, targetSdk 36,
                                      # isMinifyEnabled = true, isShrinkResources = false (keep resources!)
    src/main/AndroidManifest.xml      # uses-feature android.hardware.type.watch;
                                      # <application android:label="@string/watch_face_name" android:hasCode="false">
                                      #   <meta-data com.google.android.wearable.standalone = true />
                                      #   <property com.google.wear.watchface.format.version = 4 />
                                      #   <property com.google.wear.watchface.format.publisher = "hourly-steps-watchface-1" /> (optional)
    src/main/res/raw/watchface.xml    # the face (450 x 450)
    src/main/res/xml/watch_face_info.xml   # Preview @drawable/preview, Category, Editable true, FlavorsSupported true, MultipleInstancesAllowed true
    src/main/res/drawable/preview.png # square screenshot of the face, Violet theme, sample data
    src/main/res/values/strings.xml   # watch_face_name "Hourly Steps", slot and option names
  tools/
    validate.ps1 / validate.sh        # runs wff-validator and memory-footprint (see §9)
  README.md                           # what it is, theme table, build/install/test, limits
  demo/                               # screenshots at both emulator sizes, active + ambient, 2-3 themes
```

Wire the validator into Gradle so `./gradlew check` fails on invalid XML (an `Exec` task that runs
the jar, depended on by `check`; the GitHub samples show the pattern). That is what "pass all
tests" means for a resource-only project.

Name: watch face label **"Hourly Steps"** (the picker shows the label; the app itself is a
separate entry, so no clash). Application id `com.hourlysteps.watchface`.

---

## 9. Tooling, emulators, install

**Validator** (google/watchface repo, `third_party/wff`):

```bash
git clone https://github.com/google/watchface
cd watchface/third_party/wff && ./gradlew :specification:validator:executable-jar
java -jar specification/validator/build/libs/wff-validator.jar 4 <repo>/watchface/src/main/res/raw/watchface.xml
```

**Memory footprint** (same repo, `play-validations`):

```bash
cd watchface/play-validations && ./gradlew :memory-footprint:executable-jar
java -jar memory-footprint/build/libs/memory-footprint.jar --watch-face <apk> --schema-version 4 --ambient-limit-mb 10 --active-limit-mb 100 --apply-v1-offload-limitations --estimate-optimization
```

Clone these tools outside the project (for example `C:\Users\devon\Documents\GitHub\watchface-tools`)
and point `tools/validate.*` at the jars.

**Emulators.** Reuse `Wear_OS_6_Large_Round`; create the small one:

```bash
avdmanager create avd -n Wear_OS_6_Small_Round -k "system-images;android-36;android-wear-signed;x86_64" -d wearos_small_round
```

Install Hourly Steps on the same emulator for live data (`./gradlew installDebug` in the app repo,
grant the activity permission on first launch). On Wear OS 6 images Health Services synthesises
walking automatically, so the rings fill within minutes. Use the app's debug broadcasts to force
states (`SET_THEME`, `CELEBRATE --ez daily true` for goal-met, pause from Settings for "Paused",
a day set to Off in the week editor for "Off"). Remove the app to test the fallback.

**Install and activate the face:**

```bash
./gradlew :watchface:assembleDebug
adb install -r watchface/build/outputs/apk/debug/watchface-debug.apk
```

Android Studio's watch-face run configuration deploys and activates in one click. From the command
line, activate with the Wear debug-surface broadcast (verify the exact extras against the current
docs/samples; the operation is `set-watchface` on action `com.google.android.wearable.app.DEBUG_SURFACE`,
and the WFF renderer process is `com.google.wear.watchface.runtime`), or long-press the face on the
emulator and pick "Hourly Steps". Renderer errors:

```bash
adb logcat --pid=$(adb shell pidof -s com.google.wear.watchface.runtime)
```

Screenshots: `adb exec-out screencap -p > demo/<name>.png`.

---

## 10. Work plan (phases with done-criteria)

1. **Scaffold** — repo, branch `feature_watchface_v1`, Gradle project from §8, empty 450 × 450
   face with time only, validator wired into `check`. *Done:* `./gradlew check assembleDebug` is
   green, face installs on the large emulator and shows the time.
2. **Rings + slots** — two `ComplicationSlot`s per §7, arcs per §5, default providers pointing at
   Hourly Steps, SHORT_TEXT fallback rendering. *Done:* with the app installed both rings move with
   synthetic steps; with it uninstalled the outer ring shows system steps and the inner is hidden.
3. **Text + states** — number, caption, today line, date, 12/24 h, all rows of the state table in
   §4 (Active, Goal met colour switch, Paused, Off, `--`). *Done:* each state photographed on the
   emulator.
4. **Themes + flavors** — the seven options from §6, flavors, parity note in README. *Done:*
   editor shows the seven themes; Violet is default; screenshots of three themes.
5. **Ambient** — variant per §5. *Done:* `adb shell input keyevent KEYCODE_SLEEP` shows the dimmed
   face; memory-footprint tool passes with ambient ≤ 10 MB.
6. **Both sizes** — run on Small Round (384) and Large Round (454); fix anything that clips or
   gets too small; decide whether a `watch_face_shapes.xml` is needed (default: no). Then sideload
   to Devon's real Pixel Watch 4 (`adb` over wireless debugging) and check outdoors legibility and
   AOD with Devon. *Done:* screenshots at both sizes in `demo/`, Devon's OK on the wrist.
7. **Polish + ship** — preview.png, README, `ScreenReader` text, final validator + footprint run,
   commit, PR. *Done:* a release-signed APK builds (debug signing is acceptable for sideload).

Commit at the end of each phase on the feature branch; every commit must pass `./gradlew check
assembleDebug` plus the validator.

---

## 11. Test matrix

| Check | Small Round 384 | Large Round 454 | Pixel Watch 4 (real) |
|---|---|---|---|
| Time 12 h with AM/PM, 24 h | | | |
| Rings fill, start at 12 o'clock, round caps, correct colours | | | |
| Goal met turns outer ring `goalMet`, label changes | | | |
| Paused / Off / `--` texts | | | |
| App uninstalled → system steps fallback, inner hidden | | | |
| All 7 themes, Violet default | | | |
| Ambient: dimmed, thin rings, no clipping, no seconds | | | |
| Tap on ring opens Hourly Steps | | | |
| No text within 15 units of the round edge | | | |
| Validator clean (v4), footprint under limits | n/a | n/a | n/a |

---

## 12. Risks and open questions

- **Interval wording.** The interval source's title is "of 584" with no "this hour"; the face can't
  know the interval length. Either accept "of 584", or ask the app session to extend the title.
- **Complication refresh.** The app requests updates at most once a minute while walking and every
  300 s otherwise; the face can't poll faster. That matches the tile, so it is acceptable.
- **Exact Pixel Watch 4 pixel sizes** are best confirmed on the device (`adb shell wm size`); the
  design doesn't depend on them because WFF scales the 450 canvas.
- **Command-line activation extras** for the set-watchface broadcast changed between WFF
  versions; verify against the current docs or just use Android Studio's run configuration.
- **Two overlapping full-face slots**: the editor may show both as large tap targets. If that is
  confusing in the editor, shrink the inner slot's bounds to the inner ring's band only.
- **Theme drift** between app and face is manual (parity table). Acceptable for v1.

---

## 13. References

- WFF overview and version table: https://developer.android.com/training/wearables/wff
- Setup (manifest, watch_face_info, shapes): https://developer.android.com/training/wearables/wff/setup
- Features by version: https://developer.android.com/training/wearables/wff/features
- ComplicationSlot: https://developer.android.com/reference/wear-os/wff/complication/complication-slot
- Complication expressions: https://developer.android.com/reference/wear-os/wff/complication/complication
- DefaultProviderPolicy: https://developer.android.com/reference/wear-os/wff/complication/default-provider-policy
- Memory usage guidance: https://developer.android.com/training/wearables/wff/memory-usage
- Validator + memory footprint tools: https://github.com/google/watchface
- Samples (see `Complications` for a RANGED_VALUE arc and `Flavors`): https://github.com/android/wear-os-samples/tree/main/WatchFaceFormat
