#!/bin/bash
# Applies pipa source patches for LineageOS 24.
# Usage (from anywhere): bash device/xiaomi/pipa/patches/apply-patches.sh

PATCH_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${PATCH_DIR}/../../../.." && pwd)"

apply_patch() {
    local patch_file="${PATCH_DIR}/$1"
    local target_dir="${ROOT_DIR}/$2"

    [ -f "$patch_file" ] || { echo "[!] Patch not found: $1"; return 1; }
    [ -d "$target_dir" ] || { echo "[!] Target directory not found: $2"; return 1; }

    if git -C "$target_dir" apply --check -R "$patch_file" &>/dev/null; then
        echo "[~] Already applied: $1"
    elif git -C "$target_dir" apply --check "$patch_file" &>/dev/null; then
        echo "[+] Applying: $1"
        git -C "$target_dir" apply "$patch_file" || echo "[!] Failed to apply: $1"
    else
        echo "[!] Cannot apply: $1 (conflicts or already modified)"
    fi
}

apply_patch "Keyguard-Allow-locking-to-any-rotation-mode.patch" "frameworks/base"
apply_patch "Stop-wake-from-lid-when-LidControlsSleep-disabled.patch" "frameworks/base"
apply_patch "Desktop-mode-pill-styling/frameworks_base.patch" "frameworks/base"
apply_patch "Desktop-mode-pill-styling/packages_apps_Settings.patch" "packages/apps/Settings"
apply_patch "drm-protected-apps-frameworks_av.patch" "frameworks/av"

echo "[INFO] Patch application complete!"
