#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# --- Extract version from project.clj ---
VERSION=$(grep -m1 'defproject' "$PROJECT_DIR/project.clj" | sed 's/.*"\(.*\)".*/\1/')
# jpackage requires a numeric version (no -SNAPSHOT etc.)
CLEAN_VERSION=$(echo "$VERSION" | sed 's/-.*//')
echo "Project version: $VERSION (jpackage version: $CLEAN_VERSION)"

# --- Detect platform ---
OS="$(uname -s)"
ARCH="$(uname -m)"
case "$OS" in
    Linux*)  PLATFORM=linux ;;
    Darwin*) PLATFORM=macos ;;
    MINGW*|MSYS*|CYGWIN*) PLATFORM=windows ;;
    *) echo "Unsupported OS: $OS"; exit 1 ;;
esac

case "$ARCH" in
    x86_64|amd64) ARCH_LABEL=amd64 ;;
    aarch64|arm64) ARCH_LABEL=arm64 ;;
    *) ARCH_LABEL="$ARCH" ;;
esac

echo "Platform: $PLATFORM-$ARCH_LABEL"

# --- Locate standalone JAR ---
JAR_NAME="wacnet-${VERSION}-standalone.jar"
JAR_PATH="$PROJECT_DIR/target/$JAR_NAME"

if [[ ! -f "$JAR_PATH" ]]; then
    echo "ERROR: Standalone JAR not found at: $JAR_PATH"
    echo "Run 'lein uberjar' first to build it."
    exit 1
fi

echo "Using JAR: $JAR_PATH"

# --- Select icon ---
case "$PLATFORM" in
    linux)   ICON="$SCRIPT_DIR/icons/wacnet-256.png" ;;
    windows) ICON="$SCRIPT_DIR/icons/wacnet.ico" ;;
    macos)
        ICON="$SCRIPT_DIR/icons/wacnet.icns"
        if [[ ! -f "$ICON" ]]; then
            echo "WARNING: macOS .icns icon not found at $ICON"
            echo "Generate it on macOS with iconutil. Falling back to PNG."
            ICON="$SCRIPT_DIR/icons/wacnet-256.png"
        fi
        ;;
esac

if [[ ! -f "$ICON" ]]; then
    echo "ERROR: Icon not found at: $ICON"
    exit 1
fi

# --- Prepare staging directory ---
STAGING="$SCRIPT_DIR/staging"
rm -rf "$STAGING"
mkdir -p "$STAGING"

cp "$JAR_PATH" "$STAGING/wacnet-standalone.jar"

# --- Prepare output directory ---
DIST="$PROJECT_DIR/dist"
rm -rf "$DIST"
mkdir -p "$DIST"

# --- Run jpackage ---
echo "Running jpackage..."

jpackage \
    --type app-image \
    --name Wacnet \
    --app-version "$CLEAN_VERSION" \
    --vendor "HVAC.IO" \
    --input "$STAGING" \
    --main-jar wacnet-standalone.jar \
    --main-class wacnet.Launcher \
    --icon "$ICON" \
    --java-options "-Xmx512m" \
    --dest "$DIST"

echo "App image created at: $DIST/Wacnet"

# --- Package into archive ---
ARCHIVE_BASE="wacnet-${VERSION}-${PLATFORM}-${ARCH_LABEL}"

cd "$DIST"
ARCHIVE="${ARCHIVE_BASE}.zip"
if command -v zip &>/dev/null; then
    zip -r "$ARCHIVE" Wacnet/
elif command -v powershell &>/dev/null; then
    powershell -Command "Compress-Archive -Path 'Wacnet' -DestinationPath '$ARCHIVE'"
else
    echo "ERROR: Neither zip nor powershell found. Cannot create archive."
    exit 1
fi
echo "Archive created: $DIST/$ARCHIVE"

# --- Clean up staging ---
rm -rf "$STAGING"

echo ""
echo "Build complete!"
echo "  App image: $DIST/Wacnet/"
echo "  Archive:   $DIST/$ARCHIVE"
echo ""
echo "To test: $DIST/Wacnet/bin/Wacnet"
