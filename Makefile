PACKAGE     := io.github.raven_wing.cuview
LAUNCHER    ?= com.google.android.apps.nexuslauncher
APK         := app/build/outputs/apk/debug/app-debug.apk
APK_RELEASE := app/build/outputs/apk/release/app-release.apk
AAB_RELEASE := app/build/outputs/bundle/release/app-release.aab
MAESTRO     := $(HOME)/.maestro/bin/maestro

.PHONY: build install build-release install-release bundle test test-android test-worker lint e2e e2e-fast help

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

define reset-state
	-adb shell pm clear $(LAUNCHER)
	adb shell pm clear $(PACKAGE)
endef

e2e: install ## Build + install + run all E2E flows (state reset between each)
	$(call reset-state)
	$(MAESTRO) test e2e/flows/01_disconnect_reconnect.yaml
	$(call reset-state)
	$(MAESTRO) test e2e/flows/02_cancel.yaml
	$(call reset-state)
	$(MAESTRO) test e2e/flows/03_reconfigure.yaml

e2e-fast: ## Run all E2E flows without rebuilding or clearing state
	$(MAESTRO) test e2e/flows/01_disconnect_reconnect.yaml
	$(MAESTRO) test e2e/flows/02_cancel.yaml
	$(MAESTRO) test e2e/flows/03_reconfigure.yaml
