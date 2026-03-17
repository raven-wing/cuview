# CUeView

An Android home screen widget that displays your [ClickUp](https://clickup.com) tasks at a glance.

## Features

- Pin any list or view as a widget
- Live task list synced every 15 minutes with a manual ↻ refresh button
- Offline resilience — stale tasks are preserved on sync failure; an error banner appears instead of an empty widget
- Tap any task to open it directly in the ClickUp app (or browser)
- Secure credential storage using `EncryptedSharedPreferences` (Tink key derivation)
- OAuth 2.0 authentication via Chrome Custom Tab

## Screenshots

_TODO: add widget and config screen screenshots_

## Requirements

- Android 8.0 (API 26) or higher
- A ClickUp account with at least one workspace

## Getting Started

1. Long-press your home screen and add the **CUe View** widget.
2. Tap **Connect Workspace** — a Chrome Custom Tab opens the ClickUp OAuth page.
3. After authorizing, browse your space → folder → list hierarchy and select a list or view.
4. Tap **Save** — the widget populates with your tasks immediately.

To reconfigure, long-press the widget and select **Edit Widget** (launcher-dependent label).

## Build

Gradle requires **JDK 21** — JDK 17 and JDK 25 are not supported.

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug build uses `USE_MOCK_API=true`, which serves fake tasks without requiring real OAuth credentials.

### Module layout

| Module | Description |
|--------|-------------|
| `:app` | Widget application |
| `:lint-rules` | Custom `GlanceDpDetector` lint rule — catches raw `Int` dimensions passed to Glance (would crash at runtime) |

## Testing

### Unit tests

```bash
./gradlew :app:test
```

Uses Robolectric for tests that need Android context; plain JUnit for pure Kotlin logic.

### Lint

```bash
./gradlew :app:lintDebug         # full lint including custom Glance check
./gradlew :lint-rules:test        # lint rule unit tests
```

### End-to-end tests (Maestro)

[Maestro](https://maestro.mobile.dev/) 1.40.0 must be installed at `~/.maestro/bin/maestro`.

```bash
./e2e/run.sh [flow_name]   # defaults to 01_disconnect_reconnect
```

| Flag | Effect |
|------|--------|
| `SKIP_BUILD=1` | Skip Gradle build + ADB install |
| `SKIP_CLEAR=1` | Keep stored token between runs |

| Flow | Description |
|------|-------------|
| `00_smoke.yaml` | Place widget, verify config screen loads — no OAuth needed |
| `01_disconnect_reconnect.yaml` | Regression: connect → disconnect → reconnect → browse → save |

## CI

| Workflow | Trigger | What it does |
|----------|---------|--------------|
| `pr.yml` | PR to `main` | Builds APK, runs unit tests |
| `release.yml` | Push to `main` | Creates release PRs and GitHub releases via `release-please` |
| `e2e.yml` | PR touching `app/` or `e2e/`, or manual | Boots API 34 emulator, runs all three Maestro flows with mock API |

## Glance gotchas

- **Always use `.dp` / `.sp`** for dimensions — `padding(8)` treats the `Int` as a `@DimenRes` and crashes at runtime. The `:lint-rules` module catches this at build time.
- **Imports must be `androidx.glance.*`**, not `androidx.compose.*` — mixing causes silent runtime crashes.
- **Colors** must use `ColorProvider(R.color.x)` — hardcoded `Color(0xFF…)` values ignore system dark/light mode.
- **`LazyColumn`** translates to a `RemoteViews ListView` — keep items ≤ 15 and layouts simple.

## License

_TODO_
