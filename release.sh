#!/usr/bin/env bash
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "Usage: $0 <versionName, e.g. 1.1.0>"
  exit 1
fi

NEW_VERSION_NAME="$1"
TAG="v${NEW_VERSION_NAME}"
GRADLE_FILE="app/build.gradle.kts"

if [ ! -f "$GRADLE_FILE" ]; then
  echo "ERROR: $GRADLE_FILE not found. Run this from the project root."
  exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
  echo "ERROR: commit or stash all changes before creating a release."
  exit 1
fi

if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "ERROR: tag $TAG already exists."
  exit 1
fi

CURRENT_VERSION_CODE=$(grep 'versionCode' "$GRADLE_FILE" | head -1 | tr -dc '0-9')
NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))
CURRENT_VERSION_NAME=$(grep 'versionName' "$GRADLE_FILE" | head -1 | sed -E 's/.*"([^"]*)".*/\1/')

if [ "$CURRENT_VERSION_NAME" = "$NEW_VERSION_NAME" ]; then
  echo "ERROR: versionName is already $NEW_VERSION_NAME."
  exit 1
fi

echo "versionCode: $CURRENT_VERSION_CODE -> $NEW_VERSION_CODE"
echo "versionName: -> $NEW_VERSION_NAME"

RELEASE_BACKUP=$(mktemp)
cp "$GRADLE_FILE" "$RELEASE_BACKUP"
RELEASE_COMMITTED=false

cleanup_release() {
  if [ "$RELEASE_COMMITTED" = false ]; then
    cp "$RELEASE_BACKUP" "$GRADLE_FILE"
    git restore --staged -- "$GRADLE_FILE" >/dev/null 2>&1 || true
  fi
  rm -f "$RELEASE_BACKUP"
}
trap cleanup_release EXIT

sed -i "s/versionCode = [0-9]\+/versionCode = ${NEW_VERSION_CODE}/" "$GRADLE_FILE"
sed -i "s/versionName = \"[^\"]*\"/versionName = \"${NEW_VERSION_NAME}\"/" "$GRADLE_FILE"

./gradlew test lint assembleRelease --no-parallel

git add "$GRADLE_FILE"
git commit -m "chore: release ${TAG}"
RELEASE_COMMITTED=true
git tag -a "$TAG" -m "Release ${TAG}"
git push --atomic origin HEAD "$TAG"

trap - EXIT
rm -f "$RELEASE_BACKUP"

echo ""
echo "Pushed ${TAG}. GitHub Actions will now build, sign, and publish the release."
