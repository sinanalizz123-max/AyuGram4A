# AyuGram4A — COMPACT RESUME (2026-09-15) — paste at start of new session
# Full history: this file + ~/AyuGram4A/session.md (198 lines, untrimmed)

## 0. Where to work
- Home clone: ~/AyuGram4A = /data/data/com.termux/files/home/AyuGram4A
- Backup (DO NOT git there): /storage/emulated/0/opencode/AyuGram4A (FAT, mode-only diff)
- Rebase workdirs: ~/rebase-master (tag 11.4.2), ~/rebase-v12 master, /tmp/bootstrap (scaffold), /tmp/rebase-branch (rebase-v12 orphan)
- APKs: /storage/emulated/0/opencode/apks/ (afat release ~73MB, beta v1/v2/v3 ~79MB each, shell ~68MB)
- Keystore source: /storage/emulated/0/opencode/keystore/release-key.jks + release.env (buds key, CN=sinan.ali)
- GitHub: sinanalizz123-max/AyuGram4A (PUBLIC fork, rewrite + rebase-v12 branches) + sinanalizz123-max/AyuGram4A-private (KEPT, unused)
- Branches: rewrite (9.6.6 line, live), rebase-v12 (v12 scaffold, public fork only)
- Base pin: 7013145676d36d82ee13c02a89f72097b7490dcd (AyuGram) + 62b56a07ca7e30e39f7fd00a6728d6bbd716ca1c (official master, layer 229)
- Toolchain: JDK 17+21 local, gradle-wrapper 7.5.1→8.11.1 (v12), ANDROID_HOME ~/android-sdk (platforms 33-37, build-tools 33.0.1/36.0.0)

## 1. Secrets (NAMES ONLY — never values in git/logs)
- On BOTH repos: APP_ID, APP_HASH, SIGNING_KEY_STORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD, SIGNING_KEYSTORE_B64
- Local file: ~/AyuGram4A/local/api.properties (gitignored, trimmed prop() helper)
- Template: local/api.properties.example (placeholders only, NO maps line) — tracked
- MAPS_V2_API optional everywhere (resValue ?: "", manifest @string empty=no tiles)
- config/extera.jks REMOVED from git (.gitignore) — CI restores via base64 secret + keytool unlock gate
- APKs embed APP_ID/HASH in BuildConfig (normal, not a leak)

## 2. Current code state (rewrite HEAD ~ f86a56a)
- Bug fixes: ~60 files (NPE/AIOOB/CCE, receivers, streams, animators, thread-safety, PBKDF2, cleartext, proguard) — DONE
- Download engine: AyuDownloadEngine + AyuDownloadSpeedTest + settings UI + diagnostics + fileDownloadDegraded — DONE (DISABLED by default)
- Proprietary: upstream repo 404 — clean-room AyuMessageUtils + AyuHistoryHook (DB-backed), gitlink removed, ChatActivity loadIndex fix
- Build fix: APP_HASH missing quotes — fixed
- K1 fixes shipped: retry recovery (20 successes restore), pendingRetryDelay, flood cap 30s+jitter, balancer floor 2 + force/high exempt, mask 2s debounce + manual exempt, reason==2 routed to fail, delayed cap 8x, storage onFail(-1)
- K2 fixes shipped (instrumented v3): RETRY_LIMIT→transient backoff, flush hardening, event timeline (ring 120, FileLog dl-event + UI monospace view, 9 hooks: start/pause/flood/migrate/retry-limit/timeout/other/fail/success)
- var→explicit sweep needed for v12 (Java 1.8)
- All ci lessons: gh secret --body "$v" (not -), NDK r21e + cmake 3.10 archives, google-services beta client, refs/heads/ prefix, workflows:write rejection, push BEFORE dispatch, variant task names, keystore unlock gate

## 3. CI (fork only)
- Workflow: .github/workflows/release.yml — push on rewrite + workflow_dispatch task picker (afat/arm64/betaDebug), compile-check gate first (~10m), full build (~30-50m), secrets→local/api.properties, keystore restore, generic artifact ayugram-apk
- Rebase workflow: rebase.yml on rebase-v12 (push-triggered), clones official @ pin, --init submodules, applies overlay-rebase.tar.gz (10 files, 7.8KB), same signing
- Rate: ccache + gradle cache, 4 ABIs for afat, single ABI for debug, selectable task
- Issue: fork push events sometimes stuck — manual dispatch works; ensure workflow on correct branch visible to gh CLI (needs to be on default branch for dispatch discovery — now fixed via push trigger)

## 4. Rebase v12 (Stage 1 DONE)
- Pin master 62b56a07, layer 158→229, AGP 8.10.1, Gradle 8.11.1, JDK17, compileSdk 36, NDK 27.2.12479018, cmake 3.22.1, targetSdk 36
- AyuSync backend DEAD (ayusync.cloud refused) — local-only stubs from Stage 3
- Scaffold: package com.radolyn.ayugram, BuildVars→BuildConfig keys, passkeys off, signing extera.jks, arm64 flavor, MAPS optional, 4 manifests, google-services, gitignore
- Shell APK GREEN on rebase-v12 (libtmessages.49.so, targetSdk 36, no old-version warning) — identity verified

## 5. Download stall (OPEN, P0, throughput-dependent)
- v3 tested 3x: slow mobile 3-4 Mbps = STALL NOT REPRODUCIBLE; fast Wi-Fi 30+ Mbps = stall @15-30s — confirms server-side limit at high throughput
- v3 INSTRUMENTED: install apks/beta/debug/ayuGram-beta-v3-14092026.apk, reproduce on fast Wi-Fi, read event timeline in download details (start/err-*/retry/pause/fail) + logcat dl-event
- v1=original, v2=K1, v3=instrumented current, v4=next

## 6. Decisions locked
- Old 9.6.6 warning: LEAVE as-is (A, v12 fixes it)
- Kotlin: B — Kotlin only for NEW code in our packages; upstream stays Java
- Private repo: KEEP (do not delete)
- Fresh install not required for testing (update install OK)

## 7. Pending master plan (do in order)
- [P0] User reproduces on v3 fast Wi-Fi → paste last 4 timeline lines + Q1-Q4 (freeze% vs toast, file size/type, WiFi/mobile, screen, retry behavior, screen-on detail) → final targeted stall fix
- [P0] Also download afat-with-fixes artifact (run 34885654678) into apks/ folder — never downloaded
- [P1] R2: port com/exteragram (57 files) to rebase-v12 — overlay refresh, push, shell rebuild
- [P2] R3: port com/radolyn/ayugram + proprietary re-verify, add AyuSync dead-backend guards
- [P3] R4: adapt engine to v12 FileLoader (three-way diff first) + re-apply stop fixes
- [P4] R5: triage 60 bug-fix files (already-fixed/drop vs re-port) + var sweep + write MERGE-NOTES (integration-point map)
- [P5] Q1: main-thread I/O pass (with R5, same files) — remove allowMainThreadQueries
- [P6] R6: ship v12 afat release + device verify (login, large download+resume, camera, location) + tag
- [P6] Q2: Baseline Profiles CI job (zero code risk)
- [P6] F: future-proof — MERGE-NOTES becomes standing checklist, quarterly small merges, pin+doc, version identity
- Housekeeping: tidy apks/-prev + /tmp, empty .gitmodules, stale build remote, .beta firebase if needed

## 8. Resume commands
```bash
cd ~/AyuGram4A
gh repo view sinanalizz123-max/AyuGram4A --json visibility --jq .visibility
git status -sb; git branch -a | head
cat local/api.properties.example; git check-ignore -v local/api.properties
gh secret list -R sinanalizz123-max/AyuGram4A | cut -f1 | tr '\n' ' '
gh run list -R sinanalizz123-max/AyuGram4A --limit 3
gh run list -R sinanalizz123-max/AyuGram4A --branch rebase-v12 --limit 2
ls -lh /storage/emulated/0/opencode/apks/*.apk /storage/emulated/0/opencode/apks/beta/debug/*.apk /storage/emulated/0/opencode/apks/arm64/debug/*.apk 2>&1
# rebuild beta: gh workflow run "Build AyuGram APK" -R sinanalizz123-max/AyuGram4A --ref rewrite -f task=assembleBetaDebug
# rebase shell: push to rebase-v12 triggers automatically
```

## 9. SOP
- Concise replies; explain non-trivial bash; no code comments unless asked; mimic existing style; never commit/push unless explicitly asked (trigger=authorize)
- Restart: paste this file + ~/AyuGram4A/session.md at session start
