package com.nihilshell.wear.probe

import android.content.Context
import android.os.Build
import com.nihilshell.wear.core.Report

/**
 * The test as it goes, shared by the screen and adb (ShellProvider), and the
 * last one kept for when the process starts again. The turns of the bezel or
 * the crown count while the screen is open.
 */
object Live {
    @Volatile
    var report: Report? = null

    @Volatile
    var rotary = 0

    private const val FILE = "probe"
    private const val REPORT = "report"
    private const val RUN = "run"

    /** The next test's number, kept: tools/watch tells a fresh report from the last one by it. */
    @Synchronized
    fun next(context: Context): Int {
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val run = sp.getInt(RUN, 0) + 1
        sp.edit().putInt(RUN, run).apply()
        return run
    }

    fun save(context: Context, report: Report) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(REPORT, report.json()).apply()
    }

    /** The test now, or the last one kept, or an idle one before any. */
    fun json(context: Context): String {
        report?.let { return it.copy(rotary = rotary).json() }
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return sp.getString(REPORT, null)
            ?: Report(sp.getInt(RUN, 0), "idle", System.currentTimeMillis(), deviceName(context), Build.VERSION.SDK_INT).json()
    }
}
