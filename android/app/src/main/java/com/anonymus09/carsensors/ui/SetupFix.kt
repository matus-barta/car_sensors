package com.anonymus09.carsensors.ui

/** Something on the phone the user can change, and the screen can take them to. */
enum class SetupFix {
    /** Android's own dialog, which links on to the location page from Android 11. */
    BACKGROUND_LOCATION,

    /** Asks again for precise location. */
    PRECISE_LOCATION,

    /** The app's page in the system's settings: permissions, battery. */
    APP_SETTINGS,

    /** The app's notification page, where the switch is. */
    NOTIFICATIONS,

    /** Android's dialog to exempt the app from battery optimization. */
    BATTERY_OPTIMIZATION,

    /** The app's exemption from Data Saver. */
    DATA_SAVER,

    /** dontkillmyapp.com's page for this phone's manufacturer. */
    MANUFACTURER_GUIDE
}
