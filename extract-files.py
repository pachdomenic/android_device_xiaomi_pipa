#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: 2016 The CyanogenMod Project
# SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

import re
import os
import sys
import argparse
import subprocess

from extract_utils.fixups_blob import (
    blob_fixup,
    blob_fixups_user_type,
)
from extract_utils.main import (
    ExtractUtils,
    ExtractUtilsModule,
)

def batterysecret_rc_fixup(file_path, obj_file_path):
    """Remove seclabel line from batterysecret rc file"""
    if not obj_file_path:
        return True
    with open(obj_file_path, 'r') as f:
        content = f.read()
    content = re.sub(r"seclabel u:r:batterysecret:s0\n", "", content)
    with open(obj_file_path, 'w') as f:
        f.write(content)
    return True

def audio_primary_fixup(file_path, obj_file_path):
    """Replace string in audio primary library"""
    if not obj_file_path:
        return True
    with open(obj_file_path, 'rb') as f:
        content = f.read()
    content = content.replace(
        b"/vendor/lib/liba2dpoffload.so", 
        b"liba2dpoffload_pipa.so\x00\x00\x00\x00\x00\x00\x00"
    )
    with open(obj_file_path, 'wb') as f:
        f.write(content)
    return True

def camera_postproc_fixup(file_path, obj_file_path):
    """Run sigscan on camera postproc library"""
    if not obj_file_path:
        return True
    subprocess.run([
        os.environ.get("SIGSCAN", "sigscan"),
        "-p", "9A 0A 00 94",
        "-P", "1F 20 03 D5",
        "-f", obj_file_path
    ], check=True)
    return True

def camera_qcom_fixup(file_path, obj_file_path):
    """Replace hex string in camera qcom library"""
    if not obj_file_path:
        return True
    with open(obj_file_path, 'rb') as f:
        content = f.read()
    content = content.replace(
        b"\x73\x74\x5F\x6C\x69\x63\x65\x6E\x73\x65\x2E\x6C\x69\x63",
        b"\x63\x61\x6D\x65\x72\x61\x5F\x63\x6E\x66\x2E\x74\x78\x74"
    )
    with open(obj_file_path, 'wb') as f:
        f.write(content)
    return True

def watermark_fixup(file_path, obj_file_path):
    """Check and add libpiex_shim.so dependency"""
    if not obj_file_path:
        return True
    result = subprocess.run(
        ["grep", "-q", "libpiex_shim.so", obj_file_path], 
        stdout=subprocess.DEVNULL, 
        stderr=subprocess.DEVNULL
    )
    if result.returncode != 0:
        subprocess.run([
            os.environ.get("PATCHELF", "patchelf"),
            "--add-needed", "libpiex_shim.so",
            obj_file_path
        ], check=True)
    return True

# Define blob fixups
blob_fixups: blob_fixups_user_type = {
    'vendor/etc/init/init.batterysecret.rc': batterysecret_rc_fixup,
    'vendor/lib/hw/audio.primary.pipa.so': audio_primary_fixup,
    'vendor/lib64/vendor.qti.hardware.camera.postproc@1.0-service-impl.so': camera_postproc_fixup,
    'vendor/lib64/hw/camera.qcom.so': camera_qcom_fixup,
    'vendor/lib64/camera/components/com.mi.node.watermark.so': watermark_fixup,
}  # fmt: skip

if __name__ == '__main__':
    # Parse command line arguments first
    parser = argparse.ArgumentParser(description='Extract proprietary blobs for Xiaomi Pad 6 (pipa)')
    parser.add_argument('-s', '--section', help='Extract only specified section')
    parser.add_argument('-p', '--path', help='Path to system image or directory')
    parser.add_argument('-k', '--kang', action='store_true', help='Force extraction')
    parser.add_argument('-n', '--no-cleanup', action='store_true', help='Skip cleanup')
    parser.add_argument('--skip-common', action='store_true', help='Skip extracting common blobs')
    parser.add_argument('--device-only', action='store_true', help='Extract only device-specific blobs')
    args, remaining_args = parser.parse_known_args()

    # Set firmware handling based on section
    # If section is specified, disable firmware extraction to avoid errors
    add_firmware = not args.section

    module = ExtractUtilsModule(
        'pipa',
        'xiaomi',
        blob_fixups=blob_fixups,
        add_firmware_proprietary_file=add_firmware,
    )
    
    # Try to find the correct path to the common device
    common_device = 'sm8250-common'
    vendor = 'xiaomi'
    
    # First create the ExtractUtils instance without any options
    utils = ExtractUtils(module)
    
    # Then set its properties
    # Set clean_vendor appropriately
    if not args.no_cleanup:
        utils.clean_vendor = True
    
    # Set kang if specified
    if args.kang:
        utils.kang = True
    
    # Set section if specified
    if args.section:
        utils.section = args.section
    
    # Set source path if provided
    if args.path and args.path != 'adb':
        utils.source_path = args.path
    
    # Skip common extraction if requested or when targeting a specific section
    if not (args.skip_common or args.device_only or args.section):
        # Check if common device files exist
        standard_path = f'{os.path.dirname(os.path.abspath(__file__))}/../../{vendor}/{common_device}/proprietary-files.txt'
        
        # Add common files to be extracted
        if os.path.exists(standard_path):
            utils.add_common_files(common_device, vendor)
    
    try:
        utils.run()
    except FileNotFoundError as e:
        print(f"Error: {e}")
        print("\nSome files couldn't be found. This might be expected if you're extracting")
        print("a specific section that exists only in certain devices.")
        if args.section:
            print(f"You're extracting section '{args.section}' which might not be fully available.")
        
        # Continue with makefile generation if it was a specific section
        if args.section:
            print("Attempting to continue with available files...")
            # Try to write makefiles with what we have
            try:
                utils.write_makefiles()
                print("Successfully wrote makefiles with available files.")
            except Exception as e2:
                print(f"Failed to write makefiles: {e2}")
                sys.exit(1)
        else:
            sys.exit(1)