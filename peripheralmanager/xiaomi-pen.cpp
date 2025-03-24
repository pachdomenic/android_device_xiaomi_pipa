/*
 * Copyright (C) 2023-2025 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <iostream>
#include <unistd.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <errno.h>
#include <string.h>

#define VERSION_STRING "1.0.0"

// Device control definitions
#define SET_CUR_VALUE 0
#define TOUCH_PEN_MODE 20
#define TOUCH_MAGIC 't'
#define TOUCH_IOC_SETMODE _IO(TOUCH_MAGIC, SET_CUR_VALUE)
#define TOUCH_DEV_PATH "/dev/xiaomi-touch"

int main(int argc, char **argv) {
    // Validate arguments
    if(argc != 2) {
        fprintf(stderr, "Xiaomi pen utility v%s\n", VERSION_STRING);
        fprintf(stderr, "Usage: %s <value>\n", argv[0]);
        return -1;
    }

    // Open the touch device
    int fd = open(TOUCH_DEV_PATH, O_RDWR);
    if (fd < 0) {
        fprintf(stderr, "Error opening device %s: %s\n", 
                TOUCH_DEV_PATH, strerror(errno));
        return -1;
    }

    // Parse the input value
    int value = atoi(argv[1]);
    if (value < 0 || value > 20) {
        fprintf(stderr, "Warning: Value %d outside normal range (0-20)\n", value);
    }
    fprintf(stdout, "Setting pen mode to: %d\n", value);
    
    // Prepare and send the command
    int arg[2] = {TOUCH_PEN_MODE, value};
    int result = ioctl(fd, TOUCH_IOC_SETMODE, &arg);
    
    // Check for errors
    if (result < 0) {
        fprintf(stderr, "Error setting pen mode: %s\n", strerror(errno));
        close(fd);
        return -1;
    }

    // Clean up
    close(fd);
    fprintf(stdout, "Pen mode set successfully\n");
    return 0;
}
