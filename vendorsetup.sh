#!/bin/bash

# Function to clone if directory doesn't exist
clone_if_missing() {
    local repo_url=$1
    local branch=$2
    local target_dir=$3
    
    if [ ! -d "$target_dir" ]; then
        echo "Cloning $repo_url to $target_dir"
        git clone "$repo_url" -b "$branch" "$target_dir"
    else
        echo "$target_dir already exists, skipping clone"
    fi
}

# Git clones
clone_if_missing "https://github.com/ai94iq/android_device_xiaomi_sm8250-common" "vic" "device/xiaomi/sm8250-common"
clone_if_missing "https://github.com/ai94iq/android_kernel_xiaomi_sm8250" "vic" "kernel/xiaomi/sm8250"
clone_if_missing "https://github.com/ai94iq/proprietary_vendor_xiaomi_sm8250-common" "vic" "vendor/xiaomi/sm8250-common"
clone_if_missing "https://github.com/ai94iq/proprietary_vendor_xiaomi_pipa" "vic" "vendor/xiaomi/pipa"
clone_if_missing "https://github.com/ai94iq/android_hardware_xiaomi" "vic" "hardware/xiaomi"

# Git cherry-pick with commit check
cd bootable/recovery
git fetch https://gerrit.libremobileos.com/LMODroid/platform_bootable_recovery refs/changes/35/11735/1
COMMIT_ID=$(git rev-parse FETCH_HEAD)
if git merge-base --is-ancestor $COMMIT_ID HEAD; then
    echo "Commit already present in HEAD, skipping cherry-pick"
else
    echo "Cherry-picking commit..."
    git cherry-pick FETCH_HEAD
fi
cd ../..
