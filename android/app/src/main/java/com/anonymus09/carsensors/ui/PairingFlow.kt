package com.anonymus09.carsensors.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.anonymus09.carsensors.PortraitCaptureActivity
import com.anonymus09.carsensors.data.DevicePairing
import com.anonymus09.carsensors.data.parsePairingPayload
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * The whole pairing conversation, from the button that opens it to the last
 * dialog that closes it.
 *
 * Gathered here because it is a small state machine rather than a dialog: the
 * options card leads to the scanner or to pasting, the scanner leads to either
 * a refusal or a pairing, a pairing may first have to ask about rows recorded
 * before there was an identity, and unpairing has a confirmation of its own.
 * Only the entry point is hoisted - the caller owns whether the flow is open
 * and nothing else, so the steps between cannot be reached out of order.
 *
 * The result of pairing is not decided here. [onPair] hands the credential
 * upwards, and whether it is adopted at once or held while the question about
 * untagged rows is answered belongs to the view model.
 */
@Composable
fun PairingFlow(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    isPaired: Boolean,
    hasPendingRows: Boolean,
    untaggedRowsPendingDecision: Int?,
    onPair: (DevicePairing) -> Unit,
    onKeepUntaggedRows: () -> Unit,
    onDiscardUntaggedRows: () -> Unit,
    onCancelPairing: () -> Unit,
    onUnpair: () -> Unit
) {
    var showManualPairing by remember { mutableStateOf(false) }
    var showInvalidCode by remember { mutableStateOf(false) }
    var showConfirmUnpair by remember { mutableStateOf(false) }

    /*
     * ZXing's capture activity asks for the camera itself, so there is no
     * permission dance to run before launching it.
     */
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@rememberLauncherForActivityResult

        val pairing = parsePairingPayload(contents)

        if (pairing == null) {
            showInvalidCode = true
        } else {
            onPair(pairing)
        }
    }

    if (open) {
        PairingOptionsDialog(
            isPaired = isPaired,
            onScan = {
                onOpenChange(false)
                scanLauncher.launch(pairingScanOptions())
            },
            onEnterCode = {
                onOpenChange(false)
                showManualPairing = true
            },
            onUnpair = {
                onOpenChange(false)
                showConfirmUnpair = true
            },
            onDismiss = { onOpenChange(false) }
        )
    }

    if (showManualPairing) {
        ManualPairingDialog(
            onSubmit = { pairing ->
                showManualPairing = false
                onPair(pairing)
            },
            onDismiss = { showManualPairing = false }
        )
    }

    untaggedRowsPendingDecision?.let { rowCount ->
        UntaggedRowsDialog(
            rowCount = rowCount,
            onKeep = onKeepUntaggedRows,
            onDiscard = onDiscardUntaggedRows,
            onCancel = onCancelPairing
        )
    }

    if (showInvalidCode) {
        InvalidPairingCodeDialog(onDismiss = { showInvalidCode = false })
    }

    if (showConfirmUnpair) {
        ConfirmUnpairDialog(
            hasPendingRows = hasPendingRows,
            onConfirm = {
                showConfirmUnpair = false
                onUnpair()
            },
            onDismiss = { showConfirmUnpair = false }
        )
    }
}

/**
 * How the scanner is presented.
 *
 * Locked to QR and with the beep off: this is a setup step somebody runs
 * standing next to a car, not a checkout till. Portrait through an activity of
 * our own that the manifest locks that way - ZXing's own follows the sensor,
 * which meant turning the phone sideways to read a code from an upright screen.
 */
private fun pairingScanOptions() = ScanOptions().apply {
    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    setPrompt("Scan the pairing code shown by the web application")
    setBeepEnabled(false)
    setCaptureActivity(PortraitCaptureActivity::class.java)
    setOrientationLocked(true)
}
