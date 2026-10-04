package com.example.sparkshelper

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray

private val Context.dataStore by preferencesDataStore("targets")

object TargetStore {
    private val KEY = stringPreferencesKey("list")

    suspend fun save(ctx: Context, names: List<String>) {
        ctx.dataStore.edit { it[KEY] = JSONArray(names).toString() }
    }

    suspend fun load(ctx: Context): List<String> {
        val json = ctx.dataStore.data.first()[KEY] ?: "[]"
        val arr = JSONArray(json)
        return List(arr.length()) { arr.getString(it) }
    }
}