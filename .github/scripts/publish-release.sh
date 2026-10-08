#!/usr/bin/env bash
# Publishes release v$VERSION with $APK attached, unless it already exists.
# Notes come from the "## $VERSION" section of CHANGELOG.md, wrapped in the
# same layout as every other release.
set -euo pipefail

tag="v$VERSION"
if gh release view "$tag" >/dev/null 2>&1; then
  echo "Release $tag already exists; bump versionName to publish a new one."
  exit 0
fi

section=$(awk -v v="$VERSION" '
  /^## / { if (found) exit; if ($2 == v) { found = 1; next } }
  found { print }
' CHANGELOG.md | sed '/./,$!d')
if [ -z "$section" ]; then
  echo "::error::CHANGELOG.md has no '## $VERSION' section; add one to publish $tag."
  exit 1
fi

{
  echo "## MGM Prayer Alerts $tag"
  echo
  echo "$section"
  echo
  echo "### Install"
  echo "Download \`$APK\`, allow install from unknown sources, then grant **Notifications** and **Exact alarms**."
  echo
  echo "### Notes"
  echo "Debug build for testing. Open the app once after install so times can fetch and alarms can schedule."
  if [ "${SHARED_KEY:-false}" != "true" ]; then
    echo
    echo "If Android says the app can't be installed, uninstall the existing version first: builds signed on different machines can't update each other. This resets alert settings and custom sounds."
  fi
} > release-notes.md

gh release create "$tag" "$APK" --title "$tag" --notes-file release-notes.md --target "$GITHUB_SHA"
echo "Published $tag"
