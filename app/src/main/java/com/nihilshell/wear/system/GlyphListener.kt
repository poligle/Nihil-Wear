package com.nihilshell.wear.system

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Notification access, optional, as on the phone: NIHIL only looks at which
 * app posted a notification, its flags and its category, never at its text.
 * Each new one sends the light round the edge; a call or an alarm, twice and
 * in red. Ongoing work, silent ones and what Do Not Disturb holds back don't.
 */
class GlyphListener : NotificationListenerService() {
    private val seen = HashSet<String>()

    override fun onListenerConnected() {
        seen.clear()
        runCatching { activeNotifications }.getOrNull()?.forEach { seen += it.key }
    }

    override fun onListenerDisconnected() {
        seen.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        if (!seen.add(sbn.key) || !counts(sbn) || !alerts(sbn, rankingMap)) return
        Glyph.pulse(if (signals(sbn)) Glyph.Pulse.SIGNAL else Glyph.Pulse.NOTIFY)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) {
        seen.remove(sbn.key)
    }

    private fun signals(sbn: StatusBarNotification): Boolean =
        sbn.notification.category == Notification.CATEGORY_CALL || sbn.notification.category == Notification.CATEGORY_ALARM

    /** Not NIHIL's own, and not ongoing work or a group's summary; a call or an alarm always. */
    private fun counts(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == packageName) return false
        if (signals(sbn)) return true
        val quiet = Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_GROUP_SUMMARY
        return sbn.notification.flags and quiet == 0
    }

    private fun alerts(sbn: StatusBarNotification, map: RankingMap): Boolean {
        val ranking = Ranking()
        if (!map.getRanking(sbn.key, ranking)) return true
        return ranking.matchesInterruptionFilter() && !ranking.isAmbient
    }
}
