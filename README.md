# PearBoot

PearBoot is the official USB flashing application for pearOS, designed to create bootable USB drives directly from Android devices. This tool provides a seamless experience for users who want to install pearOS on their computers without requiring access to a desktop operating system.

## Overview

PearBoot simplifies the process of creating a bootable pearOS USB drive by handling the entire workflow within a single Android application. The app downloads the official pearOS ISO image, verifies its integrity, prepares the USB drive, writes the image, and validates the written data—all with a modern, intuitive interface.

> **Important:** PearBoot is designed and tested exclusively for flashing pearOS. Using this application with other Linux distributions or operating system images is not supported and may result in unexpected behavior.

## Features

- **Direct Download**: Downloads the official pearOS ISO image directly from pearOS servers with resume support for interrupted downloads
- **Integrity Verification**: Validates downloaded files using SHA-256 checksums to ensure data integrity before flashing
- **Automatic Drive Preparation**: Automatically wipes and prepares USB drives regardless of their current filesystem (APFS, NTFS, ext4, FAT32, etc.)
- **Raw Image Writing**: Writes the ISO image directly to the USB drive, preserving all boot structures (MBR, GPT, UEFI)
- **Post-Flash Verification**: Verifies written data against the source to confirm successful flash operations
- **Progress Tracking**: Provides detailed progress information including transfer speeds and estimated completion times
- **Verbose Logging**: Displays comprehensive logs for troubleshooting and transparency
- **Modern UI**: Built with Jetpack Compose and Material Design 3 with smooth animations and a clean interface

## Prerequisites

### Hardware Requirements

| Requirement | Specification |
|-------------|---------------|
| USB Drive | Minimum 8 GB capacity |
| Android Device | USB OTG (On-The-Go) support required |
| USB Adapter | USB OTG cable or adapter for connecting USB drives |

### Software Requirements

| Requirement | Specification |
|-------------|---------------|
| Android Version | Android 12 (API 31) or higher |
| Permissions | USB host access permission |

### Verifying OTG Support

To verify your Android device supports USB OTG:

1. Connect a USB drive using an OTG adapter
2. Check if your device recognizes the drive
3. Alternatively, use a third-party app like "USB OTG Checker" from the Play Store

## Installation

1. Download the latest APK from the [Releases](https://github.com/vikashgathala/PearBoot/releases) page
2. Enable "Install from Unknown Sources" in your device settings if prompted
3. Install the APK
4. Grant necessary permissions when requested

## Usage

### Downloading pearOS

1. Open PearBoot
2. Navigate to the **Downloads** tab
3. Tap **Download** to begin downloading the pearOS ISO
4. Wait for the download to complete and verify

### Flashing to USB

1. Connect your USB drive to your Android device using an OTG adapter
2. Navigate to the **Flash** tab
3. Ensure your USB drive is detected (shown as "Connected")
4. Tap **Flash** and confirm the operation
5. Place your device on a flat surface when prompted
6. Wait for the flashing and verification process to complete
7. Safely disconnect the USB drive

## Troubleshooting

### USB Drive Not Detected

If your USB drive is not recognized by the application:

1. Disconnect and reconnect the USB drive
2. Try using a different OTG cable or adapter
3. Test with a different USB drive to rule out hardware issues
4. Ensure your Android device supports USB OTG
5. Restart the application

If the drive still does not appear, it may be corrupted. Format the drive using a computer before attempting again.

### Flash Operation Fails or Freezes

If the flashing process fails or becomes unresponsive:

1. Do not move the device during flashing operations
2. Ensure the device has sufficient battery charge (30% or higher recommended)
3. Disconnect and reconnect the USB drive
4. Restart the application and attempt the flash again
5. Try using a different USB drive

### Download Interrupted

If the download is interrupted:

1. The application supports resume functionality
2. Simply tap **Resume** to continue from where the download stopped
3. Ensure you have a stable internet connection

## Booting from USB

After successfully creating a bootable USB drive:

### Accessing Boot Menu

1. Insert the USB drive into your computer
2. Restart or power on the computer
3. Press the appropriate key to access the boot menu during startup:

| Manufacturer | Common Boot Menu Keys |
|--------------|----------------------|
| ASUS | F8, Esc |
| Acer | F12, Esc |
| Dell | F12 |
| HP | F9, Esc |
| Lenovo | F12, F8 |
| MSI | F11 |
| Samsung | F10, Esc |
| Toshiba | F12 |
| Others | F12, F10, F8, Esc |

4. Select your USB drive from the boot menu
5. pearOS should begin loading

### BIOS/UEFI Configuration

If the USB drive does not appear in the boot menu or fails to boot:

#### Disable Secure Boot

1. Enter BIOS/UEFI settings (typically F2 or Del during startup)
2. Navigate to the **Security** or **Boot** section
3. Locate **Secure Boot** and set it to **Disabled**
4. Save changes and exit

#### Enable Legacy Boot (CSM)

1. Enter BIOS/UEFI settings
2. Navigate to the **Boot** section
3. Enable **CSM** (Compatibility Support Module) or **Legacy Boot**
4. Save changes and exit

#### Adjust Boot Priority

1. Enter BIOS/UEFI settings
2. Navigate to the **Boot** section
3. Ensure **USB Boot** is enabled
4. Move USB devices to higher priority in the boot order
5. Save changes and exit

## Technical Details



### Flash Process

1. **Initialization**: Establishes raw block device access to the USB drive
2. **Preparation**: Wipes partition tables (MBR and GPT) and filesystem signatures
3. **Writing**: Writes ISO data directly to block device starting at sector 0
4. **Verification**: Reads back written data and compares against source

