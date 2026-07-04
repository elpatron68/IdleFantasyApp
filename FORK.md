# Idle Fantasy — Fork documentation

This file describes **elpatron68/IdleFantasyApp** only. The main [README.md](README.md) follows upstream and is merged from [tristinbaker/IdleFantasy](https://github.com/tristinbaker/IdleFantasy) with minimal fork-specific edits.

## About this fork

| | Upstream | This fork |
|---|---|---|
| Repository | [tristinbaker/IdleFantasy](https://github.com/tristinbaker/IdleFantasy) | [elpatron68/IdleFantasyApp](https://github.com/elpatron68/IdleFantasyApp) |
| Releases | [Upstream releases](https://github.com/tristinbaker/IdleFantasy/releases) | [Fork releases](https://github.com/elpatron68/IdleFantasyApp/releases) |
| Signing key | Tristin's release key | Separate fork key — not interchangeable with upstream APKs |
| Internet | Not used | Optional, only for Save Viewer upload |

The fork **merges upstream regularly** (see [Staying up to date](#staying-up-to-date)) so new game content and fixes from the original app arrive here with minimal delay. Fork-specific changes (Save Viewer sync, CI) live in separate files and commits on top of upstream `main`.

Bug reports and feature requests for **core game behaviour** are best discussed upstream unless they concern fork-only features.

## Save Viewer sync

This fork can upload your in-game export JSON to the **[Idle Fantasy Save Viewer](https://gitea.elpatron.me/elpatron/Idle-Fantasy-Save-Viewer)** — a web dashboard for skills, inventory, quests, trends, and training advice.

- **Live demo:** [if-viewer.elpatron.me](https://if-viewer.elpatron.me/)
- **Viewer source:** [gitea.elpatron.me/elpatron/Idle-Fantasy-Save-Viewer](https://gitea.elpatron.me/elpatron/Idle-Fantasy-Save-Viewer)

**Setup (one-time):**

1. Open the viewer in a browser → create a personal link → **Copy link**
2. In the app: **Settings → Save Data → Save Viewer URL** → paste the link
3. **Tap Sync** on the home screen to open the viewer, or **long-press Sync** to upload; alternatively use **Send to Save Viewer** in settings

Upload uses `INTERNET` only for this optional feature. Offline play is unchanged. The viewer URL is stored in your local save; no account is required.

## Getting the app (this fork)

Download the signed APK from **[Releases](https://github.com/elpatron68/IdleFantasyApp/releases)**.

If you already have the upstream or F-Droid app installed, you must **export your save** and **uninstall** first — the fork uses a different signing key and cannot update in place.

For the original app without Save Viewer upload, see upstream [F-Droid setup](https://github.com/tristinbaker/IdleFantasy/discussions/516) or [upstream releases](https://github.com/tristinbaker/IdleFantasy/releases).

## Staying up to date

The fork stays close to upstream through GitHub Actions:

| Workflow | Trigger | What it does |
|---|---|---|
| **Upstream Sync** | Daily + manual | Merges [tristinbaker/IdleFantasy](https://github.com/tristinbaker/IdleFantasy) `main` into this fork; runs tests and builds a debug APK |
| **Upstream Release** | Hourly + manual | When upstream publishes a new GitHub Release, syncs, builds a **signed release APK**, and publishes a matching release on this fork |

To **manually trigger a release build** (requires repository secrets for the fork signing key):

1. [Actions → Upstream Release](https://github.com/elpatron68/IdleFantasyApp/actions/workflows/upstream-release.yml) → **Run workflow**
2. Optional: set `upstream_tag` (e.g. `v1.10.11`); use `force: true` to rebuild an existing tag after fork-only changes

Or from the CLI (after `gh repo set-default elpatron68/IdleFantasyApp`):

```bash
gh workflow run upstream-release.yml --ref main -f upstream_tag=v1.10.11 -f force=true
```

## Contributing (fork)

Issues and PRs in **this repository** should focus on fork-specific work (Save Viewer sync, CI, release automation). Gameplay changes are usually proposed upstream first, then merged here via the sync workflows.

## Building from source (fork)

Clone this fork and build a debug APK:

```bash
git clone https://github.com/elpatron68/IdleFantasyApp.git
cd IdleFantasyApp
./gradlew :app:assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

For a signed release APK locally, set `DEFIDE_STORE_PASSWORD` and `DEFIDE_KEY_PASSWORD` and place your keystore at `~/.android/defide-release.jks` (or set `RELEASE_KEYSTORE_PATH`), then run `./gradlew assembleRelease`. CI uses the same layout with secrets — see [upstream-release.yml](.github/workflows/upstream-release.yml).

**Note:** Debug and release APKs use different signing keys. Switching between them requires uninstalling the app (export your save first).
