package com.nihilshell.wear.deck

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import com.nihilshell.wear.core.Invitation
import com.nihilshell.wear.probe.Live

/**
 * The computer reaches the watch only through adb (tools/watch), with
 * `content call` on this provider:
 *
 *   invite  --arg nihil-deck://pair?…   the pairing QR's invitation; the watch
 *                                       pairs with it as the test opens
 *   report                              the last test, as JSON
 *   forget                              the watch forgets the computer, and
 *                                       says its id there, for nihil-deck forget
 *
 * Exported so adb's shell can call it, and refused to everything else: a
 * computer handed over by any app on the watch could be anyone's. Wireless
 * debugging's own pairing is what makes the shell the computer.
 */
class ShellProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val caller = Binder.getCallingUid()
        if (caller != Process.SHELL_UID && caller != Process.ROOT_UID) throw SecurityException("Only adb's shell talks to NIHIL here")
        val context = requireContext()
        val out = Bundle()
        when (method) {
            "invite" -> {
                val text = arg?.trim().orEmpty()
                val invitation = Invitation.parse(text) ?: throw IllegalArgumentException("Not a nihil-deck://pair invitation")
                DeckStore.get(context).invitation = text
                out.putString("invited", invitation.name)
            }
            "report" -> out.putString("report", Live.json(context))
            "forget" -> {
                val store = DeckStore.get(context)
                out.putString("device", store.computer?.device.orEmpty())
                store.forget()
            }
            else -> throw IllegalArgumentException("invite, report or forget")
        }
        return out
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
