#!/bin/bash

# Define repositories and their target directories
declare -A REPOS=(
    ["vendor/xiaomi/pipa"]="https://github.com/Matrixx-Devices/vendor_xiaomi_pipa"
    ["vendor/xiaomi/pipa-firmware"]="https://codeberg.org/CuriousNom/proprietary_vendor_xiaomi_pipa-firmware"
    ["kernel/xiaomi/pipa"]="https://github.com/CuriousNom/n0_kernel_pipa"
    ["device/qcom/wfd"]="https://github.com/Evolution-X-Devices/device_qcom_wfd"
    ["vendor/qcom/wfd"]="https://github.com/Evolution-X-Devices/vendor_qcom_wfd"
)

# Continue with other repos
for DIR in "${!REPOS[@]}"; do
    if [ -d "$DIR" ] && [ "$(ls -A "$DIR")" ]; then
        echo "[INFO] Skipping $DIR - already exists."
    else
        echo "[INFO] Cloning ${REPOS[$DIR]} into $DIR..."
        git clone --depth 1 "${REPOS[$DIR]}" "$DIR" || { echo "[ERROR] Failed to clone ${REPOS[$DIR]}"; exit 1; }
    fi
done

# Hardware/xiaomi
HW_XIAOMI_DIR="hardware/xiaomi"
HW_FORK_REPO="https://github.com/Matrixx-Devices/android_hardware_xiaomi.git"

if [ -d "$HW_XIAOMI_DIR" ]; then
    # Check if it's the fork repo
    if git -C "$HW_XIAOMI_DIR" remote get-url origin 2>/dev/null | grep -q "$HW_FORK_REPO"; then
        echo "[INFO] hardware/xiaomi is already tracking los, skipping..."
    else
        echo "[INFO] hardware/xiaomi is tracking a different repo. Replacing it with los..."
        rm -rf "$HW_XIAOMI_DIR"
        git clone --depth 1 "$HW_FORK_REPO" "$HW_XIAOMI_DIR" || { echo "[ERROR] Failed to clone los hardware/xiaomi"; exit 1; }
    fi
else
    echo "[INFO] Cloning los hardware/xiaomi..."
    git clone --depth 1 "$HW_FORK_REPO" "$HW_XIAOMI_DIR" || { echo "[ERROR] Failed to clone los hardware/xiaomi"; exit 1; }
fi

# Device Settings
DEVICESETTINGS_DIR="packages/resources/devicesettings"
DEVICESETTINGS_REPO="https://github.com/Matrixx-Devices/android_packages_resources_devicesettings.git"

if [ -d "$DEVICESETTINGS_DIR" ]; then
    # Check if it's the correct repo
    if git -C "$DEVICESETTINGS_DIR" remote get-url origin 2>/dev/null | grep -q "$DEVICESETTINGS_REPO"; then
        echo "[INFO] packages/resources/devicesettings is already tracking the correct repo, skipping..."
    else
        echo "[INFO] packages/resources/devicesettings is tracking a different repo. Replacing it..."
        rm -rf "$DEVICESETTINGS_DIR"
        git clone --depth 1 "$DEVICESETTINGS_REPO" "$DEVICESETTINGS_DIR" || { echo "[ERROR] Failed to clone devicesettings repo"; exit 1; }
    fi
else
    echo "[INFO] Cloning devicesettings repo..."
    git clone --depth 1 "$DEVICESETTINGS_REPO" "$DEVICESETTINGS_DIR" || { echo "[ERROR] Failed to clone devicesettings repo"; exit 1; }
fi

# Display HAL
DISPLAY_HAL_DIR="hardware/qcom-caf/sm8250/display"
DISPLAY_HAL_REPO="https://github.com/Matrixx-Devices/android_hardware_qcom-caf_sm8250_display.git"

if [ -d "$DISPLAY_HAL_DIR" ]; then
    # Check if it's the correct repo
    if git -C "$DISPLAY_HAL_DIR" remote get-url origin 2>/dev/null | grep -q "$DISPLAY_HAL_REPO"; then
        echo "[INFO] hardware/qcom-caf/sm8250/display is already tracking the correct repo, skipping..."
    else
        echo "[INFO] hardware/qcom-caf/sm8250/display is tracking a different repo. Replacing it..."
        rm -rf "$DISPLAY_HAL_DIR"
        git clone --depth 1 "$DISPLAY_HAL_REPO" "$DISPLAY_HAL_DIR" || { echo "[ERROR] Failed to clone display HAL repo"; exit 1; }
    fi
else
    echo "[INFO] Cloning display HAL repo..."
    git clone --depth 1 "$DISPLAY_HAL_REPO" "$DISPLAY_HAL_DIR" || { echo "[ERROR] Failed to clone display HAL repo"; exit 1; }
fi

# Apply device patches
echo "[INFO] Applying device patches..."

# Get the root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

# Function to apply a patch
apply_patch() {
    local patch_name=$1
    local target_path=$2

    local patch_file="${SCRIPT_DIR}/patches/${patch_name}"

    if [ ! -f "$patch_file" ]; then
        echo "[!] Patch not found: $patch_name"
        return 1
    fi

    local target_dir="${ROOT_DIR}/${target_path}"

    if [ ! -d "$target_dir" ]; then
        echo "[!] Target directory not found: $target_path"
        return 1
    fi

    cd "$target_dir" || return 1

    # Check if already applied (reverse check)
    if git apply --check -R "$patch_file" &>/dev/null; then
        echo "[~] Already applied: $patch_name"
    # Check if can be applied
    elif git apply --check "$patch_file" &>/dev/null; then
        echo "[+] Applying: $patch_name"
        git apply "$patch_file" || {
            echo "[!] Failed to apply: $patch_name"
            cd "$ROOT_DIR"
            return 1
        }
    else
        echo "[!] Cannot apply: $patch_name (conflicts or already modified)"
    fi

    cd "$ROOT_DIR" || return 1
}

# Add patches here
apply_patch "Keyguard-Allow-locking-to-any-rotation-mode.patch" "frameworks/base"

echo "[INFO] Patch application complete!"

echo "[INFO] All repositories are set up!"
