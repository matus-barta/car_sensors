package com.anonymus09.carsensors.util

import android.os.Build
import java.util.Locale

/**
 * dontkillmyapp.com's advice for this phone's manufacturer.
 *
 * Some manufacturers stop background apps beyond anything Android does, with
 * settings of their own that an app can neither see nor change. The site keeps
 * per-manufacturer instructions for exactly those, under pages named after
 * the manufacturer the way Android reports it. It is opened in the browser;
 * the app itself sends nothing.
 */
object ManufacturerGuide {

    private val manufacturer: String? =
        Build.MANUFACTURER.trim().takeUnless { it.isEmpty() || it.equals("unknown", true) }

    /** "Guide for Samsung phones", or a general one where Android does not say. */
    val label: String = manufacturer
        ?.let { "Guide for ${it.replaceFirstChar { c -> c.titlecase(Locale.ROOT) }} phones" }
        ?: "Guide to keeping apps running"

    val url: String = "https://dontkillmyapp.com/" +
        (manufacturer?.lowercase(Locale.ROOT)?.replace(" ", "-") ?: "")
}
