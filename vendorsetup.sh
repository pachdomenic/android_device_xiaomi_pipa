#!/bin/bash

# Define repositories and their target directories
declare -A REPOS=(
    ["vendor/xiaomi/pipa"]="https://github.com/Matrixx-Devices/vendor_xiaomi_pipa"
    ["vendor/xiaomi/pipa-firmware"]="https://codeberg.org/CuriousNom/proprietary_vendor_xiaomi_pipa-firmware"
    ["kernel/xiaomi/pipa"]="https://github.com/Matrixx-Devices/android_kernel_xiaomi_pipa"
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

echo "[INFO] All repositories are set up!"
