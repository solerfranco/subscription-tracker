package app.subscriptiontracker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Guarda la lista como JSON en SharedPreferences (alcanza para unas pocas decenas de items). */
class SubscriptionStore(context: Context) {
    private val prefs = context.getSharedPreferences("subscriptions", Context.MODE_PRIVATE)

    fun load(): List<Subscription> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val id = o.getLong("id")
                Subscription(
                    id = id,
                    name = o.getString("name"),
                    price = o.getDouble("price"),
                    cycle = BillingCycle.valueOf(o.getString("cycle")),
                    trialEndMillis = if (o.has("trialEnd")) o.getLong("trialEnd") else null,
                    category = runCatching { Category.valueOf(o.getString("category")) }.getOrDefault(Category.OTHER),
                    startMillis = if (o.has("start")) o.getLong("start") else id,
                    paused = o.optBoolean("paused", false),
                    notes = o.optString("notes", ""),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(items: List<Subscription>) {
        val arr = JSONArray()
        items.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("price", s.price)
                    put("cycle", s.cycle.name)
                    put("category", s.category.name)
                    put("start", s.startMillis)
                    put("paused", s.paused)
                    put("notes", s.notes)
                    s.trialEndMillis?.let { put("trialEnd", it) }
                },
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun loadCurrency(): String = prefs.getString(CURRENCY_KEY, "$") ?: "$"

    fun saveCurrency(symbol: String) {
        prefs.edit().putString(CURRENCY_KEY, symbol).apply()
    }

    private companion object {
        const val KEY = "items"
        const val CURRENCY_KEY = "currency"
    }
}
