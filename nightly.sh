#!/usr/bin/env bash
# nightly.sh — publish a nightly build to the self-hosted F-Droid nightly repo
#
# Usage: ./nightly.sh [nightly-number]
#   With no argument, uses one more than the highest nightly already
#   published locally for the current stable base (1 if none).
#
# What it does:
#   1. Reads the stable versionName + versionCode from app/build.gradle.kts
#      (stable codes are multiples of 1000; nightly N publishes as
#      <versionName>.N with versionCode <versionCode>+N)
#   2. Runs unit tests and builds a release-signed APK from the current
#      working tree via -PnightlyBuild=N (no commit, no tag, no clean clone)
#   3. Stages the APK into docs/fdroid-nightly/ (gitignored working repo,
#      scaffolded on first run from docs/fdroid), pruning old nightlies
#   4. Runs fdroid update and deploys to gh-pages/fdroid-nightly
#
# Testers subscribe to: https://idlefantasy.tristinbaker.xyz/fdroid-nightly/repo
# Nothing on main is committed or tagged; the stable pipeline (release.sh)
# is untouched.

set -euo pipefail

export PATH="$HOME/.local/bin:$PATH"

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLE_FILE="$REPO_DIR/app/build.gradle.kts"
STABLE_FDROID_DIR="$REPO_DIR/docs/fdroid"
NIGHTLY_DIR="$REPO_DIR/docs/fdroid-nightly"
METADATA_FILE="$REPO_DIR/metadata/com.tristinbaker.idlefantasy.yml"
FASTLANE_DIR="$REPO_DIR/fastlane/metadata/android"
APP_ID="com.tristinbaker.idlefantasy"
KEEP_NIGHTLIES=7

# ---------------------------------------------------------------------------
# Read stable version + pre-flight checks
# ---------------------------------------------------------------------------

VERSION_NAME=$(grep '^\s*versionName\s*=' "$GRADLE_FILE" | head -1 | sed 's/.*"\(.*\)".*/\1/')
VERSION_CODE=$(grep '^\s*versionCode\s*=' "$GRADLE_FILE" | head -1 | grep -oP '\d+')

if (( VERSION_CODE % 1000 != 0 )); then
    echo "ERROR: stable versionCode ($VERSION_CODE) is not a multiple of 1000."
    echo "Nightlies start with the 1.14.2 release (versionCode 141000)."
    exit 1
fi

if [[ -z "${DEFIDE_STORE_PASSWORD:-}" || -z "${DEFIDE_KEY_PASSWORD:-}" ]]; then
    echo "ERROR: DEFIDE_STORE_PASSWORD and DEFIDE_KEY_PASSWORD must be set in the environment"
    exit 1
fi

if ! command -v fdroid &>/dev/null; then
    echo "ERROR: fdroid not found on PATH (looked in $PATH)"
    exit 1
fi

if [[ ! -f "$STABLE_FDROID_DIR/config.yml" ]]; then
    echo "ERROR: $STABLE_FDROID_DIR/config.yml not found (needed to scaffold the nightly repo)"
    exit 1
fi

# ---------------------------------------------------------------------------
# Determine the nightly number
# ---------------------------------------------------------------------------

if [[ -n "${1:-}" ]]; then
    NIGHTLY_NUM="$1"
else
    LAST=0
    for apk in "$NIGHTLY_DIR"/repo/${APP_ID}_*.apk; do
        [[ -e "$apk" ]] || continue
        code=$(basename "$apk" .apk | grep -oP '\d+$')
        if (( code > VERSION_CODE && code < VERSION_CODE + 1000 && code - VERSION_CODE > LAST )); then
            LAST=$((code - VERSION_CODE))
        fi
    done
    NIGHTLY_NUM=$((LAST + 1))
fi

if (( NIGHTLY_NUM < 1 || NIGHTLY_NUM > 999 )); then
    echo "ERROR: nightly number must be 1..999, got $NIGHTLY_NUM"
    exit 1
fi

NIGHTLY_CODE=$((VERSION_CODE + NIGHTLY_NUM))
NIGHTLY_NAME="$VERSION_NAME.$NIGHTLY_NUM"

echo "==> Nightly: $NIGHTLY_NAME (versionCode $NIGHTLY_CODE)"

# ---------------------------------------------------------------------------
# Test + build from the working tree
# ---------------------------------------------------------------------------

cd "$REPO_DIR"
./gradlew testDebugUnitTest assembleRelease -PnightlyBuild="$NIGHTLY_NUM"

APK=$(find "$REPO_DIR/app/build/outputs/apk/release/" -name "*.apk" | head -1)
echo "==> Built $APK"

# ---------------------------------------------------------------------------
# Scaffold the nightly working repo on first run (whole dir is gitignored)
# ---------------------------------------------------------------------------

mkdir -p "$NIGHTLY_DIR/repo" "$NIGHTLY_DIR/metadata"

if [[ ! -f "$NIGHTLY_DIR/config.yml" ]]; then
    sed -e 's|/fdroid/repo|/fdroid-nightly/repo|' \
        -e 's|/fdroid/archive|/fdroid-nightly/archive|' \
        -e 's|repo_name: "Idle Fantasy"|repo_name: "Idle Fantasy Nightly"|' \
        -e 's|repo_description: ".*"|repo_description: "Idle Fantasy nightly builds — untested development versions for playtesters."|' \
        -e 's|archive_name: ".*"|archive_name: "Idle Fantasy Nightly Archive"|' \
        "$STABLE_FDROID_DIR/config.yml" > "$NIGHTLY_DIR/config.yml"
    chmod 600 "$NIGHTLY_DIR/config.yml"
    echo "==> Scaffolded $NIGHTLY_DIR/config.yml"
fi

cp "$STABLE_FDROID_DIR/icon.png" "$NIGHTLY_DIR/icon.png"

# App metadata: reuse the stable file with the nightly version as current
sed -e "s/^CurrentVersion:.*/CurrentVersion: $NIGHTLY_NAME/" \
    -e "s/^CurrentVersionCode:.*/CurrentVersionCode: $NIGHTLY_CODE/" \
    "$METADATA_FILE" > "$NIGHTLY_DIR/metadata/${APP_ID}.yml"
rsync -a --delete "$FASTLANE_DIR/" "$NIGHTLY_DIR/metadata/${APP_ID}/"

# ---------------------------------------------------------------------------
# Stage APK, prune old nightlies, regenerate signed index
# ---------------------------------------------------------------------------

cp "$APK" "$NIGHTLY_DIR/repo/${APP_ID}_${NIGHTLY_CODE}.apk"

PRUNE=$(ls "$NIGHTLY_DIR"/repo/${APP_ID}_*.apk 2>/dev/null | sed 's/.*_\([0-9]*\)\.apk/\1/' | sort -n | head -n -"$KEEP_NIGHTLIES" || true)
for code in $PRUNE; do
    rm -f "$NIGHTLY_DIR/repo/${APP_ID}_${code}.apk"
    echo "==> Pruned nightly versionCode $code"
done

cd "$NIGHTLY_DIR"
fdroid update --clean --pretty

# ---------------------------------------------------------------------------
# Deploy to gh-pages (fdroid-nightly/, mirrored with --delete so pruned
# nightlies disappear from the served repo too)
# ---------------------------------------------------------------------------

GH_PAGES_WORK="/tmp/gh-pages-fdroid-nightly-deploy"
rm -rf "$GH_PAGES_WORK"
cd "$REPO_DIR"
git worktree add "$GH_PAGES_WORK" gh-pages
git -C "$GH_PAGES_WORK" pull --rebase origin gh-pages
mkdir -p "$GH_PAGES_WORK/fdroid-nightly/repo"
rsync -a --delete "$NIGHTLY_DIR/repo/" "$GH_PAGES_WORK/fdroid-nightly/repo/"
cd "$GH_PAGES_WORK"
git add fdroid-nightly/
git diff --cached --quiet || git commit -m "Nightly $NIGHTLY_NAME"
git push origin gh-pages
cd "$REPO_DIR"
git worktree remove "$GH_PAGES_WORK"

echo ""
echo "======================================================"
echo "  Nightly $NIGHTLY_NAME published (versionCode $NIGHTLY_CODE)"
echo "  APK:  $APK"
echo "  Repo: https://idlefantasy.tristinbaker.xyz/fdroid-nightly/repo"
echo "======================================================"
