#!/usr/bin/env bash
# =============================================================================
# FolkPatch Debug Build Script (Linux)
# Mirrors Build-Debug.bat functionality for Linux / WSL environment
# =============================================================================

set -euo pipefail

# ── Colors ───────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

# ── Load Environment ─────────────────────────────────────────────────────────
[[ -f "${HOME}/.cargo/env" ]] && source "${HOME}/.cargo/env"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

if [[ -z "${JAVA_HOME:-}" ]]; then
    if [[ -d "${HOME}/.local/share/jvm/temurin-21" ]]; then
        export JAVA_HOME="${HOME}/.local/share/jvm/temurin-21"
    elif [[ -d "${HOME}/.jdks/temurin-21" ]]; then
        export JAVA_HOME="${HOME}/.jdks/temurin-21"
    fi
fi

if [[ -n "${JAVA_HOME:-}" && -d "${JAVA_HOME}/bin" ]]; then
    export PATH="${JAVA_HOME}/bin:${PATH}"
fi
if [[ -d "${HOME}/.cargo/bin" ]]; then
    export PATH="${HOME}/.cargo/bin:${PATH}"
fi
if [[ -d "${ANDROID_HOME}/cmdline-tools/latest/bin" ]]; then
    export PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${PATH}"
fi
if [[ -d "${ANDROID_HOME}/platform-tools" ]]; then
    export PATH="${ANDROID_HOME}/platform-tools:${PATH}"
fi

# ── Step 1: Enter apd directory ──────────────────────────────────────────────
echo -e "${BLUE}[1/4]${NC} Entering apd directory..."
if ! cd apd; then
    echo -e "${RED}Error: Failed to enter apd directory, please check if the directory exists!${NC}" >&2
    exit 1
fi

# ── Step 2: Clean apd (Rust) ─────────────────────────────────────────────────
echo -e "${BLUE}[2/4]${NC} Executing cargo clean..."
if ! cargo clean; then
    echo -e "${RED}Error: cargo clean execution failed!${NC}" >&2
    exit 1
fi

# ── Step 3: Return to parent directory ───────────────────────────────────────
echo -e "${BLUE}[3/4]${NC} Returning to parent directory..."
if ! cd ..; then
    echo -e "${RED}Error: Failed to return to parent directory!${NC}" >&2
    exit 1
fi

# ── Step 4: Build Debug APK ──────────────────────────────────────────────────
echo -e "${BLUE}[4/4]${NC} Executing ./gradlew assembleDebug..."
if ! ./gradlew assembleDebug; then
    echo -e "${RED}Error: ./gradlew assembleDebug execution failed!${NC}" >&2
    exit 1
fi

echo ""
echo -e "${GREEN}All commands executed successfully!${NC}"
echo -e "${GREEN}Debug build complete!${NC}"
APK_PATH=$(find app/build/outputs/apk/debug -name "*.apk" -type f 2>/dev/null | head -n 1)
if [[ -n "$APK_PATH" ]]; then
    echo "  Output: $APK_PATH"
fi
