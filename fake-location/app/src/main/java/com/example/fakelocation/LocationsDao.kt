package com.example.fakelocation

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class SavedLocation(
    val name: String,
    val lat: Double,
    val lng: Double
)

class LocationsDao(context: Context) {

    private val prefs = context.getSharedPreferences("saved_locations", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getAll(): MutableList<SavedLocation> {
        val json = prefs.getString("list", null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<SavedLocation>>() {}.type
        return gson.fromJson(json, type)
    }

    fun save(list: List<SavedLocation>) {
        prefs.edit().putString("list", gson.toJson(list)).apply()
    }

    fun add(location: SavedLocation) {
        val list = getAll()
        list.add(location)
        save(list)
    }

    fun removeAt(index: Int) {
        val list = getAll()
        if (index in list.indices) {
            list.removeAt(index)
            save(list)
        }
    }
}
