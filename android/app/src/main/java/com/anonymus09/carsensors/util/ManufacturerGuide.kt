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

    /** "Guide for Samsung phones", or a general one where Android does not say. */
    val label: String get() = labelFor(Build.MANUFACTURER)

    val url: String get() = urlFor(Build.MANUFACTURER)

    internal fun labelFor(manufacturer: String?): String = known(manufacturer)
        ?.let { "Guide for ${it.replaceFirstChar { c -> c.titlecase(Locale.ROOT) }} phones" }
        ?: "Guide to keeping apps running"

    /** The site's front page lists every manufacturer, for a phone that names none. */
    internal fun urlFor(manufacturer: String?): String = "https://dontkillmyapp.com/" +
        (known(manufacturer)?.lowercase(Locale.ROOT)?.replace(" ", "-") ?: "")

    // Emulators and some unbranded phones report "unknown".
    private fun known(manufacturer: String?): String? =
        manufacturer?.trim()?.takeUnless { it.isEmpty() || it.equals("unknown", true) }
}
