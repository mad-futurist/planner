package app.planner.core

/** Missing/corrupt preferences must never grant permission to transmit a new note. */
object CapturePrivacy {
    const val CONSENT_VERSION = "cloud-notes-v1"
    fun isPrivate(values: Map<String, String>): Boolean = values["cloudConsent"] != CONSENT_VERSION || values["privateCapture"] != "false"
}
