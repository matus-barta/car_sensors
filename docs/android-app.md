# The Android logger

The app records location and sensor data while a vehicle is moving and uploads it to `ingest`. It is written to live in a car unattended - typically an old handset wired to the car's power - rather than to be opened and driven by hand.

## What it is doing at any moment

The logger has three states, shown at the top of its screen and in its notification.

| State | Meaning |
| ----- | ------- |
| `STOPPED` | Switched off. Nothing is recorded and nothing is watching. |
| `WAITING FOR MOVEMENT` | On duty but parked. Sensors and GPS are off and only the hardware significant-motion sensor is listening, which costs almost nothing. |
| `RECORDING` | Moving. Sensors, GPS and the flush loop are running, and the CPU is held awake. |

The button switches the logger on and off. What it does once on is decided by movement and by the power settings, which is why "on" does not always mean "recording".

Movement promotes it from waiting to recording, but GPS then has to agree: if no fix shows the vehicle actually travelling within a short window, it goes back to waiting. That is what stops a door slamming, or the phone being picked up, from recording a journey that never happened.

## As the battery drains

Nothing is given up while the phone is on power. Off power, the logger sheds work in the order of what each part costs against what it is worth - uploads first, because the radio is the most expensive thing it does and nothing is lost by waiting; then the sample rate; then the sensors that only decorate a position; and only last does recording stop. Whichever tier is in force is named on screen, so being cut back does not look like being broken.

## Platform limitations

**A force-stopped app does not come back on its own.** If the app is stopped from Android's own application settings, the system puts the package into a stopped state in which it receives no broadcasts at all - not `BOOT_COMPLETED`, not `MY_PACKAGE_REPLACED`. "Auto-start on boot" therefore cannot recover it, and neither can rebooting the phone. Only opening the app by hand clears that state.

This is how Android treats a stopped package and there is nothing the app can do about it. It is worth knowing because the symptom - a phone that sat in a car for a week and recorded nothing - looks exactly like a bug in the app's own restoration.

**Cleartext uploads are a debug-build affordance.** Release builds do not permit plain HTTP, so a server reached over `http://` works only from a debug build. See `todo.md` for the intended relaxation, which would allow cleartext to private addresses only.
