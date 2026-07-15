package com.restaurantpos.core.network

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface TokenStore {
    fun getToken(): String?
    fun saveToken(token: String)
    fun clear()
}

@Singleton
class SharedPrefsTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) : TokenStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pos_token_store", Context.MODE_PRIVATE)

    override fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    override fun saveToken(token: String) = prefs.edit { putString(KEY_TOKEN, token) }

    override fun clear() = prefs.edit { remove(KEY_TOKEN) }

    companion object {
        private const val KEY_TOKEN = "jwt_token"
    }
}
