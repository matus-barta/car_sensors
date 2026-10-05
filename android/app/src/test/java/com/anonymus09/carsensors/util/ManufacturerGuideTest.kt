package com.anonymus09.carsensors.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which guide a phone is sent to, and what the button calls it.
 *
 * The site names its pages after the manufacturer the way Android reports it,
 * lowercased with spaces as hyphens; a phone that names no manufacturer gets
 * the front page, which lists them all, rather than a page that does not exist.
 */
class ManufacturerGuideTest {

    @Test
    fun `a manufacturer gets its own page and its name on the button`() {
        assertEquals("https://dontkillmyapp.com/samsung", ManufacturerGuide.urlFor("samsung"))
        assertEquals("Guide for Samsung phones", ManufacturerGuide.labelFor("samsung"))
    }

    @Test
    fun `Android's capitalisation is kept on the button but not in the address`() {
        assertEquals("https://dontkillmyapp.com/xiaomi", ManufacturerGuide.urlFor("Xiaomi"))
        assertEquals("Guide for Xiaomi phones", ManufacturerGuide.labelFor("Xiaomi"))
    }

    @Test
    fun `spaces become hyphens, as the site names its pages`() {
        assertEquals("https://dontkillmyapp.com/sony-mobile", ManufacturerGuide.urlFor("Sony Mobile"))
    }

    @Test
    fun `a phone that names no manufacturer gets the general guide`() {
        listOf("unknown", "Unknown", "", "   ", null).forEach {
            assertEquals("https://dontkillmyapp.com/", ManufacturerGuide.urlFor(it))
            assertEquals("Guide to keeping apps running", ManufacturerGuide.labelFor(it))
        }
    }
}
