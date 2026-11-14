#!/bin/bash
# Sourced by build/envsetup.sh: applies pipa source patches.
# The script runs in a separate bash process so it can't change the user's shell
# (no leaked variables, no cd, no exit).

bash "$(dirname "${BASH_SOURCE[0]}")/patches/apply-patches.sh"
