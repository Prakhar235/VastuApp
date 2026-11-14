package com.vastutalks.app.data.model

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Wallet and followed experts. There is no payment backend yet, so the
 * wallet balance is always 0 (nothing fake is credited); follows are kept
 * on this device only (SharedPreferences).
 */
object SampleShop {
    /** Top-up choices on the Add money sheet: amount to bonus. */
    val topUps = listOf(500 to 0, 1000 to 100, 2000 to 250, 5000 to 750)

    private var prefs: SharedPreferences? = null

    /** Real balance once payments exist; 0 until then. */
    val balance: Int = 0

    var following by mutableStateOf<Set<String>>(emptySet())
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("sample_shop", Context.MODE_PRIVATE)
        prefs = p
        // Drop the old simulated balance and store cart from earlier builds.
        p.edit().remove("balance").remove("cart").apply()
        following = p.getStringSet("following", emptySet())!!.toSet()
    }

    /** "₹0" in Indian grouping. */
    val balanceText: String get() = rupees(balance)

    fun toggleFollow(uid: String) {
        following = if (uid in following) following - uid else following + uid
        prefs?.edit()?.putStringSet("following", following)?.apply()
    }

    fun rupees(n: Int): String = "₹" + java.text.NumberFormat.getIntegerInstance(java.util.Locale("en", "IN")).format(n)
}
