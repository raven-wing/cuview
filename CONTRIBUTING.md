# Contributing to CU View

Thanks for your interest in contributing!

## License

This project is licensed under the **MIT License**. By submitting a pull request you agree that your contribution will be licensed under the same terms.

## Before You Start

For non-trivial changes, open an issue first to discuss the approach. This avoids wasted effort if the direction doesn't fit the project.

## Development Environment

**Requirements:**
- JDK 21 (not 25 — Kotlin 2.1.0 + Gradle break on JDK 25)
- Android SDK at `~/Android/Sdk` (or set `sdk.dir` in `local.properties`)
- Android device or emulator for E2E tests

**Build commands:**
```bash
make build      # build debug APK
make install    # build + deploy to device
make test       # run unit tests
make lint       # run lint (includes custom GlanceRawIntDimension check)
make e2e-smoke  # quick E2E smoke test (requires connected device)
make e2e-all    # full E2E suite with state reset
```

## Code Style

- Kotlin official code style (`kotlin.code.style=official` in `gradle.properties`)
- Detekt is enforced by CI — run `make lint` before pushing

**Glance-specific rules (easy to get wrong):**
- Always use `.dp` / `.sp` for dimensions — `padding(8)` compiles but crashes at runtime (treated as a resource ID). The custom lint rule `GlanceRawIntDimension` catches this.
- Imports must come from `androidx.glance.*`, not `androidx.compose.*` — mixing them causes silent runtime crashes
- Colors must use `ColorProvider(R.color.x)` — hardcoded `Color(0xFF...)` values ignore dark/light mode

## Bug Fixes

Every bug fix must include a test that reproduces the bug **before** the fix is applied. Add the test in the same commit as the fix.

- Use **Robolectric** for tests that need an Android context
- Use **plain JUnit** for pure Kotlin logic
- Follow existing patterns in `app/src/test/`

## Custom Lint Rules (`:lint-rules`)

The `:lint-rules` module contains `GlanceDpDetector` which enforces the `.dp`/`.sp` rule above.

Note: `LintDetectorTest` extends JUnit 3 `TestCase` — test methods must be named `testXxx`, not annotated with `@Test`.

## E2E Tests (Maestro)

E2E tests require a connected Android device and Maestro 1.40.0 installed at `~/.maestro/bin/maestro`. They are not run automatically by PR CI.

Flows live in `e2e/flows/`. Files prefixed with `_` are subflows and cannot be run directly.

**Maestro gotchas:**
- `tapOn` does not support a `timeout` property — use `extendedWaitUntil: visible:` before the tap
- Glance widget content is not in the accessibility tree — you cannot assert on task names or labels inside the widget
- `appId:` is required in every flow file, including subflows

## Pull Request Process

1. Branch from `main`
2. Make sure `make test` and `make lint` pass locally
3. CI runs automatically: build, unit tests, Detekt, Gitleaks, CodeQL, and dependency license review
4. Keep PRs focused — one thing per PR

## Reporting Bugs / Requesting Features

Open a [GitHub Issue](https://github.com/raven-wing/cuview/issues).
