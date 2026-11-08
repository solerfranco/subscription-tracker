package app.subscriptiontracker

import java.util.concurrent.TimeUnit

enum class BillingCycle { MONTHLY, YEARLY }

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
) {
    val isTrial: Boolean get() = trialEndMillis != null
}

fun Subscription.monthlyCost(): Double = when (cycle) {
    BillingCycle.MONTHLY -> price
    BillingCycle.YEARLY -> price / 12.0
}

/** Gasto mensual de las suscripciones activas; las pruebas no cuentan. */
fun monthlySpending(items: List<Subscription>): Double =
    items.filter { !it.isTrial }.sumOf { it.monthlyCost() }

fun yearlySpending(items: List<Subscription>): Double = monthlySpending(items) * 12

/** Días que faltan para que termine la prueba (negativo si ya venció); null si no es prueba. */
fun Subscription.trialDaysLeft(nowMillis: Long): Long? {
    val end = trialEndMillis ?: return null
    val diff = end - nowMillis
    // Redondea hacia arriba: "queda menos de un día" cuenta como 1.
    return Math.floorDiv(diff + TimeUnit.DAYS.toMillis(1) - 1, TimeUnit.DAYS.toMillis(1))
}
