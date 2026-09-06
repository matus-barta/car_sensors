package com.anonymus09.carsensors

import com.journeyapps.barcodescanner.CaptureActivity

/**
 * The scanner, held the way the phone is.
 *
 * ZXing's own capture activity follows the sensor, so pairing meant turning the
 * handset sideways to read a code from a screen that is upright. Locking it to
 * portrait is done through the manifest, which needs an activity of our own to
 * declare it against.
 */
class PortraitCaptureActivity : CaptureActivity()
