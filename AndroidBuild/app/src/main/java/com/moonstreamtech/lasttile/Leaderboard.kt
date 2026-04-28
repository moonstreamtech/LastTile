package com.moonstreamtech.lasttile

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class LeaderboardEntry(
    val score: Int,
    val turn: Int,
    val maxSize: Int,
    val timestamp: Long
)

interface LeaderboardProvider {
    fun topScores(limit: Int = MAX_ENTRIES): List<LeaderboardEntry>
    fun bestScore(): Int
    fun submit(entry: LeaderboardEntry): Int
    fun clear()

    companion object {
        const val MAX_ENTRIES = 10
    }
}

class LocalLeaderboard(private val prefs: SharedPreferences) : LeaderboardProvider {
    override fun topScores(limit: Int): List<LeaderboardEntry> {
        val all = load()
        return all.sortedWith(compareByDescending<LeaderboardEntry> { it.score }.thenBy { it.timestamp })
            .take(limit)
    }

    override fun bestScore(): Int = load().maxOfOrNull { it.score } ?: 0

    override fun submit(entry: LeaderboardEntry): Int {
        if (entry.score <= 0) return -1
        val merged = (load() + entry)
            .sortedWith(compareByDescending<LeaderboardEntry> { it.score }.thenBy { it.timestamp })
            .take(LeaderboardProvider.MAX_ENTRIES)
        save(merged)
        return merged.indexOf(entry)
    }

    override fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun load(): List<LeaderboardEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        LeaderboardEntry(
                            score = o.getInt("score"),
                            turn = o.optInt("turn", 0),
                            maxSize = o.optInt("maxSize", 0),
                            timestamp = o.optLong("ts", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(entries: List<LeaderboardEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            val o = JSONObject()
            o.put("score", e.score)
            o.put("turn", e.turn)
            o.put("maxSize", e.maxSize)
            o.put("ts", e.timestamp)
            arr.put(o)
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "entries_v1"
    }
}
