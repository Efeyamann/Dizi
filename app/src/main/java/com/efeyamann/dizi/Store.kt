package com.efeyamann.dizi

import android.content.Context
import org.json.JSONArray

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("dizi", Context.MODE_PRIVATE)

    var shows: List<Show>
        get() = JSONArray(p.getString("shows", "[]")).let { a -> (0 until a.length()).map { Show.fromJson(a.getJSONObject(it)) } }
        set(v) = p.edit().putString("shows", JSONArray(v.map { it.toJson() }).toString()).apply()

    var token: String
        get() = p.getString("token", "")!!
        set(v) = p.edit().putString("token", v.trim()).apply()

    var user: String
        get() = p.getString("user", "")!!
        set(v) = p.edit().putString("user", v.trim()).apply()
}
