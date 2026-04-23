PACKAGE          := io.github.raven_wing.cuview
MOCK_OAUTH_PORT  := 8765
MOCK_OAUTH_PID   := /tmp/cuview_mock_oauth.pid
LAUNCHER    ?= com.google.android.apps.nexuslauncher
APK         := app/build/outputs/apk/debug/app-debug.apk
APK_RELEASE := app/build/outputs/apk/release/app-release.apk
APK_RELEASE_TEST := app/build/outputs/apk/releaseTest/app-releaseTest.apk
AAB_RELEASE := app/build/outputs/bundle/release/app-release.aab
MAESTRO     := $(HOME)/.maestro/bin/maestro

.PHONY: build install build-release install-release bundle test test-android test-worker lint e2e e2e-fast e2e-release mock-oauth-start mock-oauth-stop help

help: ## Show this help
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

# ── build ──────────────────────────────────────────────────────────────────────

build: ## Build debug APK
	./gradlew assembleDebug

install: build ## Build and install debug APK on connected device
	adb install -r $(APK)

build-release: ## Build release APK (signed; set keystore.* in local.properties for a real key)
	./gradlew assembleRelease

install-release: build-release ## Build and install release APK on connected device
	adb install -r $(APK_RELEASE)

e2e-release: ## Build releaseTest APK (R8 on, mock API, debug-signed) + run all E2E flows
	./gradlew assembleReleaseTest
	adb uninstall $(PACKAGE) || true
	adb install $(APK_RELEASE_TEST)
	$(MAKE) mock-oauth-start
	set -e; trap '$(MAKE) mock-oauth-stop' EXIT; \
	reset() { adb shell pm clear $(LAUNCHER) || true; adb shell pm clear $(PACKAGE); }; \
	$(MAESTRO) test e2e/flows/00_chrome_setup.yaml; \
	reset; $(MAESTRO) test e2e/flows/01_disconnect_reconnect.yaml; \
	reset; $(MAESTRO) test e2e/flows/02_cancel.yaml; \
	reset; $(MAESTRO) test e2e/flows/03_reconfigure.yaml

bundle: ## Build release AAB for Play Store upload
	./gradlew bundleRelease

# ── test ───────────────────────────────────────────────────────────────────────

test: test-android test-worker ## Run all unit tests (Android + worker)

test-android: ## Run Android unit tests
	./gradlew :app:test

test-worker: ## Run OAuth worker tests
	cd worker && uv run python -m pytest tests/ -v

lint: ## Run lint checks
	./gradlew :app:lintDebug :lint-rules:test

# ── e2e ────────────────────────────────────────────────────────────────────────

mock-oauth-start: mock-oauth-stop ## Start local mock OAuth server (background); required for E2E
	python3 e2e/mock_oauth_server.py $(MOCK_OAUTH_PORT) & echo $$! > $(MOCK_OAUTH_PID)
	sleep 1
	adb reverse tcp:$(MOCK_OAUTH_PORT) tcp:$(MOCK_OAUTH_PORT)

mock-oauth-stop: ## Stop local mock OAuth server
	-kill $$(cat $(MOCK_OAUTH_PID) 2>/dev/null) 2>/dev/null
	-rm -f $(MOCK_OAUTH_PID)

e2e: install mock-oauth-start ## Build + install + run all E2E flows (state reset between each)
	set -e; trap '$(MAKE) mock-oauth-stop' EXIT; \
	reset() { adb shell pm clear $(LAUNCHER) || true; adb shell pm clear $(PACKAGE); }; \
	$(MAESTRO) test e2e/flows/00_chrome_setup.yaml; \
	reset; $(MAESTRO) test e2e/flows/01_disconnect_reconnect.yaml; \
	reset; $(MAESTRO) test e2e/flows/02_cancel.yaml; \
	reset; $(MAESTRO) test e2e/flows/03_reconfigure.yaml

# Skips rebuild and state reset; assumes Chrome is already set up (run e2e or e2e-release first).
e2e-fast: mock-oauth-start ## Run all E2E flows without rebuilding or clearing state
	set -e; trap '$(MAKE) mock-oauth-stop' EXIT; \
	$(MAESTRO) test e2e/flows/01_disconnect_reconnect.yaml; \
	$(MAESTRO) test e2e/flows/02_cancel.yaml; \
	$(MAESTRO) test e2e/flows/03_reconfigure.yaml
