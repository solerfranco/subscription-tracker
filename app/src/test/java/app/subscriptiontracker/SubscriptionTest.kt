package app.subscriptiontracker

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionTest {
    private val day = TimeUnit.DAYS.toMillis(1)

    @Test
    fun spendingIgnoresTrialsAndSpreadsYearlyPrices() {
        val items = listOf(
            Subscription(1, "Netflix", 10.0, BillingCycle.MONTHLY),
            Subscription(2, "Prime", 120.0, BillingCycle.YEARLY),
            Subscription(3, "Prueba", 50.0, BillingCycle.MONTHLY, trialEndMillis = 1000),
        )
        assertEquals(20.0, monthlySpending(items), 1e-9)
        assertEquals(240.0, yearlySpending(items), 1e-9)
    }

    @Test
    fun trialDaysLeftRoundsUp() {
        val s = Subscription(1, "X", 5.0, BillingCycle.MONTHLY, trialEndMillis = 3 * day)
        assertEquals(3L, s.trialDaysLeft(0))
        assertEquals(1L, s.trialDaysLeft(2 * day + 1))
        assertEquals(-1L, s.trialDaysLeft(5 * day - 1))
    }

    @Test
    fun nextRenewalAdvancesByCycle() {
        val monthly = Subscription(1, "X", 5.0, BillingCycle.MONTHLY, startMillis = 0)
        val next = monthly.nextRenewal(10 * day)
        assertEquals(true, next > 10 * day && next <= 10 * day + 32 * day)
        val yearly = Subscription(2, "Y", 5.0, BillingCycle.YEARLY, startMillis = 0)
        assertEquals(true, yearly.nextRenewal(10 * day) > 360 * day)
    }

    @Test
    fun categoryBreakdownAndSearch() {
        val items = listOf(
            Subscription(1, "Netflix", 10.0, BillingCycle.MONTHLY, category = Category.STREAMING),
            Subscription(2, "Spotify", 5.0, BillingCycle.MONTHLY, category = Category.MUSIC),
            Subscription(3, "Disney", 20.0, BillingCycle.MONTHLY, category = Category.STREAMING),
        )
        assertEquals(Category.STREAMING to 30.0, spendingByCategory(items).first())
        assertEquals(listOf("Netflix"), items.filterAndSort("net", SortMode.NAME, 0).map { it.name })
        assertEquals("Disney", items.filterAndSort("", SortMode.PRICE, 0).first().name)
    }

    @Test
    fun nonTrialHasNoDaysLeft() {
        assertNull(Subscription(1, "X", 5.0, BillingCycle.MONTHLY).trialDaysLeft(0))
    }
}
