package com.nihilshell.wear

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import com.nihilshell.wear.deck.DeckStore
import com.nihilshell.wear.ui.AppList
import com.nihilshell.wear.ui.Nihil

/**
 * NIHIL on the watch: the computer's deck first, then the watch's apps by
 * name, then the network test. Opened from its icon, or from NIHIL's watch
 * face, which links here (nihil-wear://home).
 */
class HomeActivity : Activity() {
    private lateinit var list: AppList

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        list = AppList(this).apply { onOpen = { open(it) } }
        setContentView(list)
    }

    override fun onResume() {
        super.onResume()
        list.entries = entries()
        list.requestFocus()
    }

    private fun entries(): List<AppList.Entry> {
        val computer = DeckStore.get(this).computer
        val deck = if (computer != null) {
            AppList.Entry(DECK, computer.name, getString(R.string.home_deck), Nihil.INK)
        } else {
            AppList.Entry(TEST, getString(R.string.home_pair), getString(R.string.home_pair_note), Nihil.OFF)
        }
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0L))
            .filter { it.activityInfo.packageName != packageName }
            .map { AppList.Entry(ComponentName(it.activityInfo.packageName, it.activityInfo.name).flattenToString(), it.loadLabel(packageManager).toString()) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        return listOf(deck) + apps + AppList.Entry(TEST, getString(R.string.probe_title))
    }

    private fun open(entry: AppList.Entry) {
        val intent = when (entry.key) {
            DECK -> Intent(this, DeckActivity::class.java)
            TEST -> Intent(this, ProbeActivity::class.java)
            else -> Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName.unflattenFromString(entry.key))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        runCatching { startActivity(intent) }
    }

    companion object {
        private const val DECK = "nihil:deck"
        private const val TEST = "nihil:test"
    }
}
