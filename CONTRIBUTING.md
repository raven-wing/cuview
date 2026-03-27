# Contributing to CU View

Thanks for your interest in contributing!

## License

This project is licensed under the **MIT License**. By submitting a pull request you agree that your contribution will be licensed under the same terms.

## Before You Start

For non-trivial changes, open an issue first to discuss the approach. This avoids wasted effort if the direction doesn't fit the project.

## Development Setup

**Requirements:**

- JDK 21 — JDK 17 and JDK 25 are not supported (Kotlin 2.1.0 + Gradle break on JDK 25).
  Set it in `~/.gradle/gradle.properties` (not `local.properties` — Gradle CLI does not read that file):
  ```
  org.gradle.java.home=/usr/lib/jvm/java-21-openjdk
  ```
- Android SDK at `~/devel/android/sdk`. `local.properties` already points to it.
- A connected Android device or emulator for E2E tests.

## Building

```bash
make install                     # build debug APK with mock API and install on device
USE_MOCK_API=false make install  # build debug APK with real ClickUp API and install
make build-release               # release APK (requires keystore configured in local.properties)
```

Debug builds default to `USE_MOCK_API=true`, which serves fake tasks without requiring real OAuth credentials. Set `USE_MOCK_API=false` to test the real OAuth and sync flow.

## Testing

```bash
make test   # run all unit tests (Android + worker)
make lint   # run lint checks, including the custom GlanceRawIntDimension rule
make e2e    # build + install + run all Maestro E2E flows (state reset between each)
make e2e-fast  # run all E2E flows without rebuilding or clearing state
```

E2E tests require a connected Android device and Maestro 1.40.0 installed at `~/.maestro/bin/maestro`.

## Module Layout

| Module | Description |
|--------|-------------|
| `:app` | Widget application |
| `:lint-rules` | Custom `GlanceDpDetector` lint rule — catches raw `Int` dimensions passed to Glance (would crash at runtime) |

## Branch Conventions

- `code` is the main development branch — branch from here for new work.
- `main` is release-only — do not target it directly with feature branches.

## Bug Fixes

Every bug fix must include a test that reproduces the bug **before** the fix is applied. Add the test in the same commit as the fix.

- Use **Robolectric** for tests that need an Android context.
- Use **plain JUnit** for pure Kotlin logic.
- Follow existing patterns in `app/src/test/`.

## Code Style

- Kotlin official code style (`kotlin.code.style=official` in `gradle.properties`).

**Glance-specific rules (easy to get wrong):**

- Always use `.dp` / `.sp` for dimensions — `padding(8)` compiles but crashes at runtime (treated as a resource ID). The custom lint rule `GlanceRawIntDimension` catches this.
- Imports must come from `androidx.glance.*`, not `androidx.compose.*` — mixing them causes silent runtime crashes.
- Colors must use `ColorProvider(R.color.x)` — hardcoded `Color(0xFF...)` values ignore dark/light mode.

## Custom Lint Rules (`:lint-rules`)

The `:lint-rules` module contains `GlanceDpDetector` which enforces the `.dp`/`.sp` rule above.

Note: `LintDetectorTest` extends JUnit 3 `TestCase` — test methods must be named `testXxx`, not annotated with `@Test`.

## E2E Tests (Maestro)

Flows live in `e2e/flows/`. Files prefixed with `_` are subflows and cannot be run directly.

**Maestro gotchas:**

- `tapOn` does not support a `timeout` property — use `extendedWaitUntil: visible:` before the tap.
- Glance widget content is not in the accessibility tree — you cannot assert on task names or labels rendered inside the widget.
- `appId:` is required in every flow file, including subflows.

## Pull Request Process

1. Branch from `code`.
2. Make sure `make test` and `make lint` pass locally.
3. Keep PRs focused — one thing per PR.

## Reporting Bugs / Requesting Features

Open a [GitHub Issue](https://github.com/raven-wing/cuview/issues).
