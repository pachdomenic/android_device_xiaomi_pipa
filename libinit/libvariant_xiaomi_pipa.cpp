/*
 * Copyright (C) 2021-2025 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <libvariant.h>

static const variant_info cmi_info = {
    .hwc_value = "",
    .sku_value = "",

    .brand = "Xiaomi",
    .device = "pipa",
    .marketname = "",
    .model = "Pad 6",
    .build_fingerprint = "Xiaomi/pipa_global/pipa:13/RKQ1.211001.001/V816.0.7.0.UMZMIXM:user/release-keys",

    .nfc = true,
};

const std::vector<variant_info> variants = {
    cmi_info,
};
