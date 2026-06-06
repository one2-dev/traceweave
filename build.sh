#!/usr/bin/env bash
set -euo pipefail

# Maps the friendly env var names onto the ugly names Gradle / the publish plugins expect, then
# runs Gradle. With no args it does the full release publish (CI use). With args it runs exactly
# those tasks — e.g. local signing check:
#   ./build.sh :compiler:signMavenPublication -Pversion=0.0.1

# --- Gradle Plugin Portal ---
export GRADLE_PUBLISH_KEY="${GRADLE_PLUGIN_PUBLISH_KEY:-}"
export GRADLE_PUBLISH_SECRET="${GRADLE_PLUGIN_PUBLISH_SECRET:-}"

# --- Maven Central ---
export ORG_GRADLE_PROJECT_mavenCentralUsername="${MAVEN_CENTRAL_USERNAME:-}"
export ORG_GRADLE_PROJECT_mavenCentralPassword="${MAVEN_CENTRAL_PASSWORD:-}"
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword="${MAVEN_CENTRAL_SIGNING_PASSWORD:-}"

# SIGNING_KEY_B64 = `gpg --export-secret-keys --armor <KEY_ID> | base64 -w0`.
if [ -n "${MAVEN_CENTRAL_SIGNING_KEY_B64:-}" ]; then
  ORG_GRADLE_PROJECT_signingInMemoryKey="$(echo "$MAVEN_CENTRAL_SIGNING_KEY_B64" | base64 --decode)"
  export ORG_GRADLE_PROJECT_signingInMemoryKey
  echo "--- signing key ---"
  echo "$ORG_GRADLE_PROJECT_signingInMemoryKey" | head -1
  echo "$ORG_GRADLE_PROJECT_signingInMemoryKey" | wc -l
fi

# `build.sh local` → build + publish to ~/.m2 (sign check). No args → real release publish.
mode="${1:-}"

if [ "$mode" = "local" ]; then
  tasks="clean build publishToMavenLocal"
else
  tasks="clean build publishAllPublicationsToMavenCentralRepository :gradle-plugin:publishPlugins -Pgradle.publish.key=$GRADLE_PUBLISH_KEY -Pgradle.publish.secret=$GRADLE_PUBLISH_SECRET"
fi

if grep -qiE "microsoft|wsl" /proc/version 2>/dev/null; then
  # WSL → Windows gradle via the alias: forward the vars it reads, and clear $@ so the alias's
  # own $@ doesn't re-inject the script's args (e.g. `local`) into the build.
  export WSLENV=${WSLENV:-}:ORG_GRADLE_PROJECT_mavenCentralUsername/w:ORG_GRADLE_PROJECT_mavenCentralPassword/w:ORG_GRADLE_PROJECT_signingInMemoryKey/w:ORG_GRADLE_PROJECT_signingInMemoryKeyPassword/w:GRADLE_PUBLISH_KEY/w:GRADLE_PUBLISH_SECRET/w
  set --
  gradle $tasks
else
  ./gradlew $tasks
fi

