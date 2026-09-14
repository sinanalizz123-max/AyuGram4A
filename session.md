# AyuGram4A - Session Resume File

> Use this file to resume the previous opencode session about AyuGram4A.
> Created: 2026-09-14

## 1. Task List (What the User Asked For)

1. Fork `https://github.com/AyuGram/AyuGram4A` to user's GitHub — **DONE**
2. Clone the fork locally in home — **DONE**
3. Copy a backup into `/storage/emulated/0/opencode/` (shared storage) — **DONE**
4. Inspect the ENTIRE codebase and find ALL bugs (quick-on-look first, then deeper, one-by-one, hold nothing back) — **IN PROGRESS**
5. Write this session.md for continuing the conversation in another session — **DONE**

## 2. Context / Environment

- Platform: Termux (Android) on Linux
- GitHub account: `sinanalizz123-max` (authenticated via `gh`, token scopes include repo)
- Main work dir: `~/AyuGram4A` = `/data/data/com.termux/files/home/AyuGram4A`
- Shared-storage backup: `/storage/emulated/0/opencode/AyuGram4A` (also via `~/storage/shared/opencode/AyuGram4A` symlink)
- Repo size: ~924 MB, ~20,985 files, .git ~539 MB
- NO rsync available; copy used `cp -a -u`
- Shared storage copy shows "dubious ownership" -> `git config --global --add safe.directory` was already added; do NOT do git work there (FAT-like fs, file modes differ)

## 3. Git State

Fork repo on GitHub: `https://github.com/sinanalizz123-max/AyuGram4A` (parent = AyuGram/AyuGram4A)

### Home clone `~/AyuGram4A`
- Branch: `rewrite`, tracked vs `origin/rewrite`
- HEAD commit: `701314567`
- Working tree: CLEAN
- Remotes:
  - `origin`   = https://github.com/sinanalizz123-max/AyuGram4A.git
  - `upstream` = https://github.com/AyuGram/AyuGram4A.git

### Storage copy `/storage/emulated/0/opencode/AyuGram4A`
- Same remotes as above, same HEAD `701314567`
- `git status` there shows 534 files with mode-only changes (0 insertions/deletions) — normal on shared storage, NOT a real diff. Do not touch.

## 4. Codebase Structure (for inspection)

- Mod-specific code (MOST LIKELY bug-prone, AyuGram-specific features):
  - `TMessagesProj/src/main/java/com/exteragram/messenger/` (ExteraConfig.java, ExteraResources.java, subdirs: boost/, camera/, components/, icons/, preferences/, utils/)
- Core app code (Telegram upstream + AyuGram mods):
  - `TMessagesProj/src/main/java/org/telegram/messenger/`
  - `TMessagesProj/src/main/java/org/telegram/ui/`
  - `TMessagesProj/src/main/java/org/telegram/tgnet/`
- Key single files: `ExteraConfig.java`, `SharedConfig.java`, `BuildVars.java`, `AndroidUtilities.java`, `MessagesController.java` (huge), `NotificationsController.java`, `ImageLoader.java`, `MediaController.java`, `ApplicationLoader.java`, `FileLoader.java`
- Build files: top-level `build.gradle`, `TMessagesProj/build.gradle`, `gradle.properties`, `TMessagesProj/src/main/AndroidManifest.xml`
- Other top-level: `tlrpc-patch.py`, `apkdiff.py`, `crowdin.yml`

## 5. Bug Inspection Progress

### What has been scanned so far

1. **TODO/FIXME/XXX/HACK/BUG grep** across `org/telegram/messenger` — mostly library-code false positives (androidx.recyclerview) and AUTODOWNLOAD bit-mask false positives. Real ones noted:
   - `ApplicationLoader.java:154` `//TODO improve` (LocaleController init)
   - `ApplicationLoader.java:205,227` `//TODO improve account`
   - `EmuDetector.java:316` `//TODO scoped storage`
   - `MediaController.java:2195` `//TODO topics`
   - `MessagesController.java:2074,3243,15798` TODOs
   - `MessagesStorage.java:397,7003,8459` TODOs
   - `NotificationsController.java:3085,3230,4824` TODOs (incl. "7.3.0 bug fix" comment)
   - `SecretChatHelper.java:1229` TODO
   - `TopicsController.java:938`, `WearReplyReceiver.java:83` TODO topics
   - `FingerprintManagerCompat.java:228` TODO
   - `audioinfo/mp3/ID3v2Info.java:85`, `MP3Frame.java:195` TODOs
   - `FormatCache.java:34` TODO

2. **WeakRef .get() checks** (`DownloadController`, `ImageLoader`, `Browser`, `MusicBrowserService`, `AndroidUtilities`) — mostly properly null-checked; LOW risk, no action yet.

3. **Resource-leak grep** (FileInputStream/FileOutputStream/BufferedReader/Cursor/ContentResolver):
   - `AndroidUtilities.java:1561` openAssetFileDescriptor — need to verify close
   - `AndroidUtilities.java:1571` BufferedReader — need to verify close
   - `AndroidUtilities.java:3317` `copyFile(sourceFile, new FileOutputStream(destFile))` — need to verify stream close upstream
   - `AndroidUtilities.java:4732` FileInputStream — verify close
   - `ChatThemeController.java:386` FileOutputStream — verify close
   - `ContactsController.java` many Cursor usages `623,809,1954,1983,2713` — verify close in finally
   - **=> NEXT: audit try/finally around these**

4. **Exception-handling grep in exteragram mod code** — many `catch (Exception e)` + `e.printStackTrace()` found:
   - `utils/TranslatorUtils.java:124-125` printStackTrace
   - `camera/CameraXController.java:168,172,481,488,571` printStackTrace
   - `utils/MonetUtils.java:113-115` printStackTrace
   - `utils/ChatUtils.java:301`, `AppUtils.java:106`, `camera/CameraXUtils.java:91`, `utils/UpdaterUtils.java` (many), `utils/LocaleUtils.java`, `components/MessageDetailsPopupWrapper.java`, `utils/FontUtils.java`, `utils/SystemUtils.java`, `preferences/OtherPreferencesActivity.java:107`, `preferences/components/DoubleTapCell.java:273,376` — mostly swallowed exceptions (moderate concern, not all bugs)
   - **=> NEXT: read these files and judge each catch**

### Still to be inspected (NEXT SESSION)

- [ ] Full read of `com/exteragram/messenger/**` — ExteraConfig, ExteraResources, boost/, camera/, components/, icons/, preferences/, utils/ (mod bugs: NPEs, thread-safety, main-thread violations, logic errors)
- [ ] Full read of core messenger files: AndroidUtilities, ImagesCache, ImageLoader, MediaController, MessagesController, NotificationsController, FileLoader, SecretChatHelper, SendMessagesHelper, ContactsController, ChatThemeController, SharedConfig, BuildVars, ApplicationLoader
- [ ] Build files: `build.gradle`, `TMessagesProj/build.gradle`, `gradle.properties`, manifest(s) — SDK versions, proguard, jni/NDK config, google-services
- [ ] Security sweep: hardcoded keys/tokens/secrets (grep `apiKey|secret|token|password|Bearer`), crypto usage (AES/ECDH in tgnet), weak TLS config, network security
- [ ] Thread-safety sweep: `SharedPreferences`, `HashMap/HashSet/ArrayList` statics mutated off-main-thread, `synchronized` audit on `SharedConfig`, `UsersController`, `ChatObject` caches
- [ ] The reported diff-spots in home repo vs common AOSP warnings (lint/compile warnings) — run `./gradlew lint` if environment permits (very heavy on Termux; may skip)

## 6. Commands / Paths Cheat Sheet

```bash
cd ~/AyuGram4A
git remote -v                          # origin + upstream
git status -sb                         # expect clean on 'rewrite'
gh repo view sinanalizz123-max/AyuGram4A --json url,parent

# re-verify storage backup integrity
du -sh ~/AyuGram4A /storage/emulated/0/opencode/AyuGram4A
git -C /storage/emulated/0/opencode/AyuGram4A rev-parse --short HEAD

# if storage copy needs refresh FROM home copy:
cp -a -u "$HOME/AyuGram4A/." "/storage/emulated/0/opencode/AyuGram4A/"
```

## 7. SOP / Rules for Continuation

- User speaks casual English; answer concisely, update the user when long operations run
- User wants: "inspect code one by one, don't hold back, tell me ALL bugs and problems"
- Read-only burst first, then report bugs grouped by severity; ask before fixing anything
- NEVER commit/push unless explicitly asked
- Do not add comments to code unless asked
- For new/edited files follow existing conventions (Java 8+, Android; upstream Telegram style)

## 8. Session 2 (2026-09-14) — build mode, all done except CI run

- Bug fixes from §5 all IMPLEMENTED (~60 files, no commit): NPE/AIOOB/CCE guards, receiver unregister+EXPORTED, stream/FD finally-close, animator/dialog/window leak fixes, volatile/sync, FolderIcons logic, PBKDF2 passcode, cleartext=false, proguard narrowed
- Download engine IMPLEMENTED: `com/exteragram/messenger/utils/AyuDownloadEngine.java` (new), `download/AyuDownloadSpeedTest.java` (new), hooks in FileLoadOperation/FileLoader/FileLoaderPriorityQueue, settings UI in GeneralPreferencesActivity, diagnostics in MessageDetailsPopupWrapper, `fileDownloadDegraded` event in NotificationCenter
- Telegram API VERIFIED live: Telethon `auth.sendCode` accepted (phone_code_hash issued), getConfig 19 DCs; temp venv removed afterwards
- Credentials: real values in gitignored `local/api.properties` (trimmed); `local/api.properties.example` = placeholders, NO maps line; build.gradle loads `local/api.properties` (+API_KEYS fallback) via trimmed `prop()` helper; MAPS_V2_API optional (`?: ""`); manifest meta-data kept (empty = no tiles, no crash)
- Build fix found by real compile: APP_HASH buildConfigField was missing quotes (pre-existing) — FIXED
- Proprietary submodule DEAD upstream (repo 404) → replaced with clean-room `com/radolyn/ayugram/proprietary/AyuMessageUtils.java` + `AyuHistoryHook.java` (same API, both mapping directions, DB-backed doHook); gitlink removed from index + .gitmodules cleaned (staged, NOT committed); ChatActivity.doHook call now passes loadIndex
- CI: `.github/workflows/release.yml` rewritten for ubuntu-22.04 hosted runners (checkout v4, temurin 17, NDK r21e + cmake 3.10.2 from dl.google.com archives, secrets → local/api.properties, `assembleAfatRelease`, artifact upload)
- On-device build NOT possible: NDK 21.4/cmake 3.10 gone from sdkmanager, NDK toolchains are x86_64-only, Room sqlite-jdbc needs glibc; installed build-tools 33.0.1 + NDK 23.1 locally for validation only (machine-local `-I local-ndk.init.gradle`, repo untouched)
- USER MUST: add 5 repo Secrets (APP_ID, APP_HASH, SIGNING_KEY_STORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD), then push to `rewrite` or Run workflow manually

## 9. Session 3 (2026-09-14) — private repo + cloud-assembled CI build

- Original fork made PRIVATE, but GitHub disabled pushes on it (fork restriction) → created NEW private non-fork repo `sinanalizz123-max/AyuGram4A-private`
- 5 Secrets set on BOTH repos (values never printed); only secret NAMES visible via `gh secret list`
- Phone upload too slow for 500MB history push (HTTP 408 x2) → bootstrap strategy: runner clones public upstream base (`7013145676d36d82ee13c02a89f72097b7490dcd`, verified reachable) via partial clone, applies 507KB `overlay.tar.gz` (68 files: all our changes, NO secrets), then builds
- Bootstrap commit pushed to AyuGram4A-private:rewrite (519KB: release.yml + overlay.tar.gz + BASE_SHA) — CI run triggered
- Builder files live in `~/.cache/opencode/tmp/bootstrap/` (reusable for future pushes: refresh overlay.tar.gz + push)
- To rebuild after new edits: regenerate overlay.tar.gz from `git diff --name-only <base> HEAD` (minus `.github/workflows/release.yml`) + push to AyuGram4A-private:rewrite

## 10. Session 4 (2026-09-14) — back to public fork, building there

- Fork re-made PUBLIC (private-fork pushes were 403-disabled by GitHub); AyuGram4A-private kept as spare
- Secrets NEVER in git: only `local/api.properties.example` (placeholders) tracked — verified in commit + leak-scan of workflow/build.gradle
- Push to fork needed only 2-commit delta (history already there) — succeeded instantly
- Push events not triggering runs (disable-cycle leftover) → build triggered via manual `workflow_dispatch`: run 34829805662 on AyuGram4A:rewrite
- NOTE: built APK embeds APP_ID/APP_HASH in BuildConfig like every Telegram client — normal, not a leak; raw secrets stay in Actions Secrets + local file only

## 11. Session 5 (2026-09-14) — secrets bug found + fixed

- CI failed at `int APP_ID = ;` → added lengths-only diagnostic step → ALL secrets were single `-`
- Root cause: `gh secret set --body -` does NOT read stdin; it stored the literal dash. Re-set all 5 with `--body "$v"` on both repos
- Lengths gate now PASSES (APP_ID 8, APP_HASH 32); full build running: run 34835506611 on AyuGram4A:rewrite

## 12. Session 6 (2026-09-14) — CI iterating to green

- Run 34835506611 reached Java compile (native OK) but 2 type errors in our edits: popup Context→Activity, FileLog.e(String,String) — FIXED, pushed, dispatched 34840727479 (stale 34840661527 cancelled: dispatched before push landed)
- Lesson: always push BEFORE dispatching; verify run's commit matches