ADB_EMULATOR   := adb -e
MOCK_OAUTH_PID   := /tmp/cuview_mock_oauth.pid
MOCK_OAUTH_PORT  := 8765
PACKAGE          := io.github.raven_wing.cuview
LAUNCHER         ?= com.google.android.apps.nexuslauncher
APK              := app/build/outputs/apk/debug/app-debug.apk
APK_RELEASE      := app/build/outputs/apk/release/app-release.apk
APK_RELEASE_TEST := app/build/outputs/apk/releaseTest/app-releaseTest.apk
AAB_RELEASE      := app/build/outputs/bundle/release/app-release.aab
MAESTRO          := $(HOME)/.maestro/bin/maestro
AVD_NAME         := pixel_6_api34_google_apis
EMULATOR_FLAGS   := -avd $(AVD_NAME) -no-snapshot-load -no-snapshot-save -accel on -gpu swangle_indirect -noaudio -no-boot-anim

.PHONY: build install build-release install-release bundle test test-android test-worker lint e2e _e2e-flows e2e-act start-emulator start-emulator-windowed stop-emulator mock-oauth-start mock-oauth-stop help

# ── shared: state reset between flows ──────────────────────────────────────────
# Wipes launcher widget index and cuview data, cycles the cuview package so the
# AppWidget service rebinds providers, restores the home screen, then polls until
# cuview's widget provider is re-registered (a fixed sleep is unreliable on CI).
define wait-for-boot
	@echo "Emulator PID $$(cat /tmp/emulator.pid); log: /tmp/emulator.log"
	@timeout=300; \
	until [ "$$($(ADB_EMULATOR) shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do \
	  if ! kill -0 "$$(cat /tmp/emulator.pid)" 2>/dev/null; then \
	    echo "Emulator process exited. Last 30 log lines:"; \
	    tail -30 /tmp/emulator.log; \
	    exit 1; \
	  fi; \
	  [ $$timeout -le 0 ] && { echo "Emulator boot timed out. Last 30 log lines:"; tail -30 /tmp/emulator.log; exit 1; }; \
	  sleep 3; timeout=$$((timeout - 3)); \
	done; \
	echo "Emulator booted (boot_completed=1)"
endef

define reset-state
	adb shell am force-stop $(LAUNCHER) 2>/dev/null || true
	adb shell am force-stop com.android.chrome 2>/dev/null || true
	adb shell pm clear $(LAUNCHER) || true
	adb shell pm clear com.android.chrome || true
	adb shell pm clear $(PACKAGE)
	adb shell pm disable $(PACKAGE) 2>/dev/null || true
	adb shell pm enable $(PACKAGE) 2>/dev/null || true
	adb shell input keyevent KEYCODE_HOME
	@ok=0; \
	for i in $$(seq 1 20); do \
	  if adb shell dumpsys appwidget 2>/dev/null | grep -Fq "$(PACKAGE)/"; then \
	    ok=1; break; \
	  fi; \
	  sleep 1; \
	done; \
	[ $$ok -eq 1 ] || { echo "Widget provider did not re-register after 20s"; exit 1; }
	# Wait for launcher search index to catch up (provider registration != searchable).
	@sleep 5
endef

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

e2e: ## Build releaseTest APK (R8 on, mock API, debug-signed) + run all E2E flows
	./gradlew assembleReleaseTest
	adb uninstall $(PACKAGE) || true
	adb install $(APK_RELEASE_TEST)
	mkdir -p e2e/recordings
	adb shell settings put global window_animation_scale 0
	adb shell settings put global transition_animation_scale 0
	adb shell settings put global animator_duration_scale 0
	$(MAKE) mock-oauth-start
	$(MAKE) _e2e-flows || { $(MAKE) mock-oauth-stop; exit 1; }
	$(MAKE) mock-oauth-stop

# Private target: runs all Maestro flows with state resets between them.
# Kept separate so the single `|| mock-oauth-stop` in `e2e` covers all
# failure paths — including reset-state failures — not just Maestro exits.
_e2e-flows:
	$(call reset-state)
	./e2e/run_with_recording.sh 01_disconnect_reconnect $(MAESTRO) test e2e/flows/01_disconnect_reconnect.yaml
	$(call reset-state)
	./e2e/run_with_recording.sh 02_cancel               $(MAESTRO) test e2e/flows/02_cancel.yaml
	$(call reset-state)
	./e2e/run_with_recording.sh 03_reconfigure          $(MAESTRO) test e2e/flows/03_reconfigure.yaml

# Runs the CI workflow locally via act + Podman.
# Uses catthehacker/ubuntu:full-22.04 which matches ubuntu-latest (Android SDK + ANDROID_HOME included).
# Gradle cache is shared with the host to avoid re-downloading on each run.
# First run will download the API 34 google_apis system image (~1.5 GB).
e2e-act: ## Run CI E2E workflow locally via act + Podman (mirrors ubuntu-latest)
	act -W .github/workflows/e2e.yml \
	  -P ubuntu-latest=catthehacker/ubuntu:full-22.04 \
	  --container-options "--device /dev/kvm --privileged -v $(HOME)/.gradle:/root/.gradle" \
	  -j e2e \
	  --container-daemon-socket "unix:///run/user/$$(id -u)/podman/podman.sock"

mock-oauth-start: mock-oauth-stop ## Start local mock OAuth server (background); required for E2E
	# nohup + setsid detach from this shell's session so the server survives the
	# per-line subshell exit and any GitHub Actions step cleanup. </dev/null cuts
	# stdin so the orphaned process can't get SIGTTIN. Logs to /tmp/mock_oauth.log
	# for post-mortem if the OAuth flow ever 404s/times out.
	@rm -f /tmp/mock_oauth.log
	nohup setsid python3 e2e/mock_oauth_server.py $(MOCK_OAUTH_PORT) \
	  </dev/null >/tmp/mock_oauth.log 2>&1 & echo $$! > $(MOCK_OAUTH_PID)
	# kill -0 before curl so a port collision (pre-existing server) can't false-pass.
	@pid=$$(cat $(MOCK_OAUTH_PID)); \
	for i in $$(seq 1 15); do \
	  kill -0 $$pid 2>/dev/null || { echo "mock OAuth server crashed; log:"; tail -20 /tmp/mock_oauth.log; exit 1; }; \
	  curl -fsS http://127.0.0.1:$(MOCK_OAUTH_PORT)/ -o /dev/null && \
	    echo "mock OAuth server up on :$(MOCK_OAUTH_PORT) (PID $$pid)" && break; \
	  [ $$i -eq 15 ] && { echo "mock OAuth server failed to start; log:"; tail -20 /tmp/mock_oauth.log; exit 1; }; \
	  sleep 1; \
	done
	# Forward emulator's localhost:$(MOCK_OAUTH_PORT) to host's $(MOCK_OAUTH_PORT) so
	# the app's CCT URL `http://localhost:$(MOCK_OAUTH_PORT)/` reaches the mock server.
	# This bypasses the emulator's internal NAT (10.0.2.2 → host) which is unreliable
	# under -no-window headless mode on CI.
	adb reverse tcp:$(MOCK_OAUTH_PORT) tcp:$(MOCK_OAUTH_PORT) || { $(MAKE) mock-oauth-stop; exit 1; }

mock-oauth-stop: ## Stop local mock OAuth server (no-op if not running)
	-@adb reverse --remove tcp:$(MOCK_OAUTH_PORT) 2>/dev/null
	-@kill $$(cat $(MOCK_OAUTH_PID) 2>/dev/null) 2>/dev/null
	@rm -f $(MOCK_OAUTH_PID)

start-emulator: ## Start CI-matching emulator (Pixel 6, API 34, headless) — run before make e2e
	# Both local and CI use `-gpu swangle_indirect`: ANGLE-on-SwiftShader, pure CPU.
	# It's the only mode that satisfies both constraints we care about:
	#   - ANGLE API → Pixel Launcher's drag-overlay (resize handles, reconfigure
	#     pencil in flow 03) receives taps correctly; under `swiftshader_indirect`
	#     the pencil-button tap is silently dropped.
	#   - SwiftShader backend → Chrome 113's GPU compositor (CCT page load in
	#     flow 01) doesn't segfault; under `angle_indirect` on a GPU-less CI
	#     runner Chrome's CompositorGpuTh hits a null vtable pointer (SIGSEGV).
	# Locally on Fedora, SwiftShader needs SELinux execheap — run once:
	#   sudo setsebool -P selinuxuser_execheap 1
	#
	# Stderr/stdout goes to /tmp/emulator.log; the polling loop checks the emulator
	# PID before each iteration so a silent crash fails fast instead of timing out.
	@rm -f /tmp/emulator.log
	@nohup setsid emulator $(EMULATOR_FLAGS) -no-window \
	  </dev/null >/tmp/emulator.log 2>&1 & echo $$! > /tmp/emulator.pid
	$(call wait-for-boot)

start-emulator-windowed: ## Start windowed emulator for local dev (same AVD + GPU as start-emulator)
	@rm -f /tmp/emulator.log
	@nohup setsid emulator $(EMULATOR_FLAGS) \
	  </dev/null >/tmp/emulator.log 2>&1 & echo $$! > /tmp/emulator.pid
	$(call wait-for-boot)

stop-emulator: ## Stop running emulator (graceful via adb, falls back to PID kill)
	-@$(ADB_EMULATOR) emu kill 2>/dev/null
	-@pid=$$(cat /tmp/emulator.pid 2>/dev/null); \
	  if [ -n "$$pid" ] && ps -p "$$pid" -o command= 2>/dev/null | grep -Eq '(^|/)(emulator|qemu-system.*)($$| )'; then \
	    kill "$$pid" 2>/dev/null; \
	  fi
	@rm -f /tmp/emulator.pid

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
