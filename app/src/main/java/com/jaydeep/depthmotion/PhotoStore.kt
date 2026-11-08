package com.jaydeep.depthmotion

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray

object PhotoStore {
    private const val PREFS = "depthmotion"
    private const val PHOTOS = "photos"
    private const val TREE = "tree"
    private const val INDEX = "index"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun photos(c: Context): MutableList<String> = try {
        val a = JSONArray(prefs(c).getString(PHOTOS, "[]"))
        MutableList(a.length()) { i -> a.getString(i) }
    } catch (_: Exception) { mutableListOf() }

    fun add(c: Context, uri: Uri) {
        val list = photos(c)
        if (uri.toString() !in list) list.add(uri.toString())
        save(c, list)
    }
    fun addTree(c: Context, uri: Uri) {
        prefs(c).edit().putString(TREE, uri.toString()).apply()
        discover(c, uri)
    }
    fun discover(c: Context, uri: Uri? = tree(c)) {
        if (uri == null) return
        val root = DocumentFile.fromTreeUri(c, uri) ?: return
        val found = photos(c)
        fun walk(dir: DocumentFile, depth: Int) {
            if (depth > 8) return
            dir.listFiles().forEach { f ->
                if (f.isDirectory) walk(f, depth + 1)
                else if (f.isFile && (f.type?.startsWith("image/") == true) && f.uri.toString() !in found)
                    found.add(f.uri.toString())
            }
        }
        walk(root, 0)
        save(c, found)
    }
    fun tree(c: Context): Uri? = prefs(c).getString(TREE, null)?.let(Uri::parse)
    fun save(c: Context, list: List<String>) {
        val a = JSONArray()
        list.forEach(a::put)
        prefs(c).edit().putString(PHOTOS, a.toString()).apply()
        if (prefs(c).getInt(INDEX, 0) >= list.size) prefs(c).edit().putInt(INDEX, 0).apply()
    }
    fun next(c: Context): String? {
        discover(c)
        val list = photos(c)
        if (list.isEmpty()) return null
        val p = prefs(c)
        val shuffle = p.getBoolean("shuffle", false)
        val current = p.getInt(INDEX, 0).coerceIn(0, list.lastIndex)
        val next = if (shuffle && list.size > 1) {
            var n = current
            while (n == current) n = (0 until list.size).random()
            n
        } else (current + 1) % list.size
        p.edit().putInt(INDEX, next).apply()
        return list[next]
    }
    fun current(c: Context): String? {
        val list = photos(c)
        if (list.isEmpty()) return null
        val i = prefs(c).getInt(INDEX, 0).coerceIn(0, list.lastIndex)
        return list[i]
    }
    fun interval(c: Context) = prefs(c).getLong("interval", 60L)
    fun setInterval(c: Context, minutes: Long) = prefs(c).edit().putLong("interval", minutes).apply()
}
