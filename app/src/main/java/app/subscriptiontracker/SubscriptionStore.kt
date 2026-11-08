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
                Subscription(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    price = o.getDouble("price"),
                    cycle = BillingCycle.valueOf(o.getString("cycle")),
                    trialEndMillis = if (o.has("trialEnd")) o.getLong("trialEnd") else null,
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
                    s.trialEndMillis?.let { put("trialEnd", it) }
                },
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "items"
    }
}
