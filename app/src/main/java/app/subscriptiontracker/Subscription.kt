package app.subscriptiontracker

import java.util.Calendar
import java.util.concurrent.TimeUnit

enum class BillingCycle { MONTHLY, YEARLY }

enum class Category(val label: String) {
    STREAMING("Streaming"),
    MUSIC("Música"),
    GAMES("Juegos"),
    PRODUCTIVITY("Productividad"),
    CLOUD("Nube"),
    OTHER("Otros"),
}

enum class SortMode(val label: String) {
    RENEWAL("Renovación"),
    PRICE("Precio"),
    NAME("Nombre"),
}

/**
 * Una suscripción activa o una prueba gratuita ([trialEndMillis] != null).
 * En una prueba, [price] es lo que se pagaría al terminar.
 */
data class Subscription(
    val id: Long,
    val name: String,
    val price: Double,
    val cycle: BillingCycle,
    val trialEndMillis: Long? = null,
    val category: Category = Category.OTHER,
    /** Fecha del primer cobro; de ahí salen las renovaciones. Por defecto, cuando se creó. */
    val startMillis: Long = id,
    /** Pausada: no se cuenta en los gastos ni en los próximos cobros. */
    val paused: Boolean = false,
    val notes: String = "",
) {
    val isTrial: Boolean get() = trialEndMillis != null

    /** Cuenta para el gasto: ni prueba ni pausada. */
    val isCharging: Boolean get() = !isTrial && !paused
}

fun Subscription.monthlyCost(): Double = when (cycle) {
    BillingCycle.MONTHLY -> price
    BillingCycle.YEARLY -> price / 12.0
}

/** Gasto mensual de las suscripciones activas; las pruebas no cuentan. */
fun monthlySpending(items: List<Subscription>): Double =
    items.filter { it.isCharging }.sumOf { it.monthlyCost() }

fun yearlySpending(items: List<Subscription>): Double = monthlySpending(items) * 12

/** Próxima renovación después de [nowMillis]; para una prueba, el fin de la prueba. */
fun Subscription.nextRenewal(nowMillis: Long): Long {
    trialEndMillis?.let { return it }
    val stepMonths = if (cycle == BillingCycle.MONTHLY) 1 else 12
    var n = 0
    while (true) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = startMillis
            add(Calendar.MONTH, n * stepMonths)
        }
        if (cal.timeInMillis > nowMillis) return cal.timeInMillis
        n++
    }
}

/** Días hasta la próxima renovación (0 = hoy o en menos de un día). */
fun Subscription.daysUntilRenewal(nowMillis: Long): Long {
    val diff = nextRenewal(nowMillis) - nowMillis
    return Math.floorDiv(diff + TimeUnit.DAYS.toMillis(1) - 1, TimeUnit.DAYS.toMillis(1))
}

/** Gasto mensual por categoría (solo activas), de mayor a menor. */
fun spendingByCategory(items: List<Subscription>): List<Pair<Category, Double>> =
    items.filter { it.isCharging }
        .groupBy { it.category }
        .map { (c, list) -> c to list.sumOf { it.monthlyCost() } }
        .sortedByDescending { it.second }

/** Lo que se cobra en los próximos [days] días (suscripciones activas). */
fun upcomingCharges(items: List<Subscription>, nowMillis: Long, days: Int): Double =
    items.filter { it.isCharging && it.daysUntilRenewal(nowMillis) <= days }.sumOf { it.price }

data class Currency(val symbol: String, val label: String)

val currencies = listOf(
    Currency("$", "Peso / Dólar ($)"),
    Currency("€", "Euro (€)"),
    Currency("£", "Libra (£)"),
    Currency("¥", "Yen (¥)"),
    Currency("R$", "Real (R$)"),
)

fun List<Subscription>.filterAndSort(query: String, sort: SortMode, nowMillis: Long): List<Subscription> {
    val filtered = if (query.isBlank()) this else filter { it.name.contains(query.trim(), ignoreCase = true) }
    return when (sort) {
        SortMode.RENEWAL -> filtered.sortedBy { it.nextRenewal(nowMillis) }
        SortMode.PRICE -> filtered.sortedByDescending { it.monthlyCost() }
        SortMode.NAME -> filtered.sortedBy { it.name.lowercase() }
    }
}

/** Días que faltan para que termine la prueba (negativo si ya venció); null si no es prueba. */
fun Subscription.trialDaysLeft(nowMillis: Long): Long? {
    val end = trialEndMillis ?: return null
    val diff = end - nowMillis
    // Redondea hacia arriba: "queda menos de un día" cuenta como 1.
    return Math.floorDiv(diff + TimeUnit.DAYS.toMillis(1) - 1, TimeUnit.DAYS.toMillis(1))
}
