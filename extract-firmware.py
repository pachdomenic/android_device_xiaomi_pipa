#!/usr/bin/env python3
#
# SPDX-FileCopyrightText: 2024 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

import os
import sys
import shutil
import argparse
import glob
import hashlib
import re
from pathlib import Path

def calculate_sha1(file_path):
    """Calculate SHA1 checksum for a file"""
    sha1 = hashlib.sha1()
    with open(file_path, 'rb') as f:
        while True:
            data = f.read(65536)  # Read in 64k chunks
            if not data:
                break
            sha1.update(data)
    return sha1.hexdigest()

def extract_firmware_images(source_dir, device="pipa", vendor="xiaomi", update_mk=True):
    """
    Copy firmware image files specified in proprietary-firmware.txt to the vendor directory
    and update Android.mk with SHA1 checksums
    
    Args:
        source_dir: Directory containing firmware images
        device: Device codename
        vendor: Vendor name
        update_mk: Whether to update the Android.mk file
    """
    # Convert to absolute path
    source_dir = os.path.abspath(source_dir)
    
    # Destination directory
    vendor_dir = os.path.abspath(f"{os.path.dirname(os.path.abspath(__file__))}/../../..")
    vendor_firmware_dir = f"{vendor_dir}/vendor/{vendor}/{device}/radio"
    
    # Path to the firmware files list
    firmware_list_path = f"{os.path.dirname(os.path.abspath(__file__))}/proprietary-firmware.txt"
    
    # Check if firmware list exists
    if not os.path.exists(firmware_list_path):
        print(f"Error: Firmware list not found at {firmware_list_path}")
        return False
        
    # Read firmware list
    firmware_files = []
    with open(firmware_list_path, 'r') as f:
        for line in f:
            # Skip comments and empty lines
            line = line.strip()
            if not line or line.startswith('#'):
                continue
                
            # Extract the filename from the line
            # Format is typically: path/to/file.img (or similar)
            parts = line.split()
            if parts:
                firmware_files.append(os.path.basename(parts[0]))
    
    if not firmware_files:
        print(f"No firmware files specified in {firmware_list_path}")
        return False
        
    # Create the vendor firmware directory if it doesn't exist
    os.makedirs(vendor_firmware_dir, exist_ok=True)
    
    # Find the firmware files in the source directory
    found_files = []
    missing_files = []
    
    for firmware_file in firmware_files:
        # Search for the file in the source directory
        matches = []
        for root, dirs, files in os.walk(source_dir):
            if firmware_file in files:
                matches.append(os.path.join(root, firmware_file))
        
        if matches:
            # Use the first match
            found_files.append((matches[0], firmware_file))
        else:
            missing_files.append(firmware_file)
    
    if missing_files:
        print("Warning: The following firmware files were not found:")
        for file in missing_files:
            print(f"  - {file}")
    
    if not found_files:
        print(f"No firmware files found in {source_dir}")
        return False
    
    # Copy the firmware files to vendor directory
    copied_files = []
    sha1_entries = []
    
    for source_file, filename in found_files:
        dest_file = f"{vendor_firmware_dir}/{filename}"
        
        print(f"Copying {filename} to {vendor_firmware_dir}")
        shutil.copy2(source_file, dest_file)
        copied_files.append(filename)
        
        # Calculate SHA1 checksum
        sha1_hash = calculate_sha1(dest_file)
        sha1_entries.append((filename, sha1_hash))
    
    print(f"\nSuccessfully copied {len(copied_files)} firmware files:")
    for file in copied_files:
        print(f"  - {file}")
    
    # Update files if requested
    if update_mk and sha1_entries:
        # 1. Update Android.mk
        android_mk_path = f"{vendor_dir}/vendor/{vendor}/{device}/Android.mk"
        
        # Check if Android.mk exists
        if not os.path.exists(android_mk_path):
            print(f"Creating new Android.mk at {android_mk_path}")
            create_new_android_mk = True
        else:
            # Check if file has the device conditional
            with open(android_mk_path, 'r') as f:
                existing_content = f.read()
            if f"ifeq ($(TARGET_DEVICE),{device})" in existing_content:
                create_new_android_mk = False
            else:
                print(f"Android.mk exists but doesn't have device conditional for {device}")
                print(f"Creating new Android.mk at {android_mk_path}")
                create_new_android_mk = True
        
        # Create file or add firmware entries to existing file
        if create_new_android_mk:
            # Create a new Android.mk file with our firmware entries
            mk_content = """# Automatically generated file. DO NOT MODIFY
#
# This file is generated by device/{vendor}/{device}/extract-firmware.py

LOCAL_PATH := $(call my-dir)

ifeq ($(TARGET_DEVICE),{device})

""".format(vendor=vendor, device=device)

            # Add all firmware entries
            for filename, sha1 in sha1_entries:
                mk_content += f"$(call add-radio-file-sha1-checked,radio/{filename},{sha1})\n"
            
            # Close the conditional
            mk_content += "\nendif"
            
            # Write the file
            with open(android_mk_path, 'w') as f:
                f.write(mk_content)
        else:
            # File exists and has device conditional
            # Find the position to insert the radio files
            with open(android_mk_path, 'r') as f:
                mk_content = f.read()
            
            device_line = f"ifeq ($(TARGET_DEVICE),{device})"
            endif_line = "endif"
            
            device_pos = mk_content.find(device_line)
            endif_pos = mk_content.find(endif_line, device_pos)
            
            if device_pos != -1 and endif_pos != -1:
                # Calculate where to insert firmware entries (after device line)
                insert_pos = device_pos + len(device_line)
                
                # Build the radio files section
                radio_section = "\n\n"
                for filename, sha1 in sha1_entries:
                    radio_section += f"$(call add-radio-file-sha1-checked,radio/{filename},{sha1})\n"
                
                # Insert the radio section
                new_content = mk_content[:insert_pos] + radio_section + mk_content[insert_pos:]
                
                # Write the updated content
                with open(android_mk_path, 'w') as f:
                    f.write(new_content)
            else:
                print(f"Error: Malformed Android.mk - creating new one")
                # Create a new file as fallback
                mk_content = """# Automatically generated file. DO NOT MODIFY
#
# This file is generated by device/{vendor}/{device}/extract-firmware.py

LOCAL_PATH := $(call my-dir)

ifeq ($(TARGET_DEVICE),{device})

""".format(vendor=vendor, device=device)

                # Add all firmware entries
                for filename, sha1 in sha1_entries:
                    mk_content += f"$(call add-radio-file-sha1-checked,radio/{filename},{sha1})\n"
                
                # Close the conditional
                mk_content += "\nendif"
                
                # Write the file
                with open(android_mk_path, 'w') as f:
                    f.write(mk_content)
        
        print(f"\nUpdated {android_mk_path} with firmware SHA1 checksums")
        
        # 2. Update BoardConfigVendor.mk
        board_config_path = f"{vendor_dir}/vendor/{vendor}/{device}/BoardConfigVendor.mk"

        # Extract all partition names (remove .img extension)
        partition_names = []
        for filename, _ in sha1_entries:
            name = os.path.splitext(filename)[0]  # Remove extension
            partition_names.append(name)

        # Check if BoardConfigVendor.mk exists and has content
        if not os.path.exists(board_config_path) or os.path.getsize(board_config_path) == 0:
            print(f"Creating new BoardConfigVendor.mk at {board_config_path}")
            
            # Create a new BoardConfigVendor.mk file with our AB_OTA_PARTITIONS
            board_content = """# Automatically generated file. DO NOT MODIFY
#
# This file is generated by device/{vendor}/{device}/extract-firmware.py

AB_OTA_PARTITIONS += \\
""".format(vendor=vendor, device=device)

            # Add all partitions
            for partition in sorted(partition_names):
                if partition == sorted(partition_names)[-1]:  # Last item
                    board_content += f"    {partition}"
                else:
                    board_content += f"    {partition} \\\n"
            
            # Write the file
            with open(board_config_path, 'w') as f:
                f.write(board_content)
                
            print(f"Created BoardConfigVendor.mk with AB_OTA_PARTITIONS")
        else:
            # File exists with content, update it
            with open(board_config_path, 'r') as f:
                board_content = f.read()
            
            # Check if AB_OTA_PARTITIONS already exists
            ab_ota_match = re.search(r'AB_OTA_PARTITIONS \+= \\(.*?)(?=\n\n|\n[^\s]|\Z)', board_content, re.DOTALL)
            
            if ab_ota_match:
                # Get existing content
                existing_content = ab_ota_match.group(1)
                
                # Check which partitions are already listed
                existing_partitions = []
                for line in existing_content.split('\n'):
                    line = line.strip()
                    if line and not line.endswith('\\'):  # Last line won't have continuation
                        existing_partitions.append(line)
                    elif line:  # Has continuation
                        existing_partitions.append(line[:-1].strip())
                
                # Filter out partitions that already exist
                new_partitions = [p for p in partition_names if p not in existing_partitions]
                
                if new_partitions:
                    # Build the new section
                    new_section = "AB_OTA_PARTITIONS += \\"
                    for partition in sorted(existing_partitions + new_partitions):
                        new_section += f"\n    {partition} \\"
                    
                    # Remove trailing backslash from the last line
                    new_section = new_section[:-2]
                    
                    # Replace the old section
                    board_content = board_content.replace(ab_ota_match.group(0), new_section)
                    
                    with open(board_config_path, 'w') as f:
                        f.write(board_content)
                    
                    print(f"Updated {board_config_path} with new partitions")
            else:
                # AB_OTA_PARTITIONS doesn't exist in the file, add it
                new_section = "\nAB_OTA_PARTITIONS += \\"
                for partition in sorted(partition_names):
                    if partition == sorted(partition_names)[-1]:  # Last item
                        new_section += f"\n    {partition}"
                    else:
                        new_section += f"\n    {partition} \\"
                
                # Add to the end of the file
                with open(board_config_path, 'a') as f:
                    f.write(new_section + "\n")
                
                print(f"Added AB_OTA_PARTITIONS to {board_config_path}")
    
    return True

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Extract firmware files from ROM image')
    parser.add_argument('source', nargs='?', help='Path to extracted ROM directory')
    parser.add_argument('--no-mk-update', action='store_true', help='Do not update Android.mk')
    
    args = parser.parse_args()
    
    if not args.source:
        parser.print_help()
        print("\nError: Source directory is required")
        sys.exit(1)
    
    success = extract_firmware_images(args.source, update_mk=not args.no_mk_update)
    sys.exit(0 if success else 1)