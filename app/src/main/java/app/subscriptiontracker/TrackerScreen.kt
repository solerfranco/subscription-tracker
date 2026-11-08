package app.subscriptiontracker

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private fun money(v: Double) = String.format(Locale.getDefault(), "$%.2f", v)

private fun cycleLabel(c: BillingCycle) = if (c == BillingCycle.MONTHLY) "mes" else "año"

private val categoryColors = mapOf(
    Category.STREAMING to Color(0xFFE0507A),
    Category.MUSIC to Color(0xFF8E5BE0),
    Category.GAMES to Color(0xFF2FA84F),
    Category.PRODUCTIVITY to Color(0xFF3B82F6),
    Category.CLOUD to Color(0xFF00A8C8),
    Category.OTHER to Color(0xFF8A8F98),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(
    items: List<Subscription>,
    onAdd: (name: String, price: Double, cycle: BillingCycle, trialDays: Int?, category: Category) -> Unit,
    onEdit: (Subscription, name: String, price: Double, cycle: BillingCycle, category: Category) -> Unit,
    onDelete: (Subscription) -> Unit,
    onConvertTrial: (Subscription) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Subscription?>(null) }
    var toDelete by remember { mutableStateOf<Subscription?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(SortMode.RENEWAL) }
    val now = System.currentTimeMillis()
    val active = items.filter { !it.isTrial }
    val trials = items.filter { it.isTrial }.sortedBy { it.trialEndMillis }
    val shown = active.filterAndSort(query, sort, now)

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Agregar") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SummaryCard(items, active.size, now) }

            val byCategory = spendingByCategory(items)
            if (byCategory.size > 1) {
                item { CategoryBreakdown(byCategory) }
            }

            if (trials.isNotEmpty()) {
                item { SectionTitle("Períodos de prueba") }
                items(trials, key = { it.id }) { s ->
                    val days = s.trialDaysLeft(now) ?: 0
                    val status = when {
                        days < 0 -> "Venció hace ${-days} d: cancelalo ya"
                        days == 0L -> "Termina hoy: cancelalo ya"
                        days == 1L -> "Queda 1 día"
                        else -> "Quedan $days días"
                    }
                    SubscriptionCard(
                        sub = s,
                        subtitle = "$status · después ${money(s.price)}/${cycleLabel(s.cycle)}",
                        urgent = days <= 2,
                        actions = {
                            TextButton(onClick = { onConvertTrial(s) }) { Text("Me quedo") }
                            TextButton(onClick = { onDelete(s) }) { Text("Cancelé") }
                        },
                    )
                }
            }

            item { SectionTitle("Suscripciones") }
            if (active.size > 1) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = CircleShape,
                    )
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SortMode.entries.forEach { m ->
                            FilterChip(selected = sort == m, onClick = { sort = m }, label = { Text(m.label) })
                        }
                    }
                }
            }
            if (active.isEmpty()) {
                item { Text("Todavía no agregaste ninguna.", style = MaterialTheme.typography.bodyMedium) }
            } else if (shown.isEmpty()) {
                item { Text("Sin resultados.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(shown, key = { it.id }) { s ->
                val days = s.daysUntilRenewal(now)
                val renewal = when (days) {
                    0L -> "se renueva hoy"
                    1L -> "se renueva mañana"
                    else -> "se renueva en $days días"
                }
                SubscriptionCard(
                    sub = s,
                    subtitle = "${money(s.price)}/${cycleLabel(s.cycle)} · $renewal",
                    urgent = days <= 2,
                    actions = {
                        IconButton(onClick = { editing = s }) { Icon(Icons.Default.Edit, contentDescription = "Editar") }
                        IconButton(onClick = { toDelete = s }) { Icon(Icons.Default.Delete, contentDescription = "Eliminar") }
                    },
                )
            }
        }
    }

    if (showAdd) {
        SubscriptionDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onConfirm = { name, price, cycle, trialDays, category ->
                onAdd(name, price, cycle, trialDays, category)
                showAdd = false
            },
        )
    }
    editing?.let { s ->
        SubscriptionDialog(
            initial = s,
            onDismiss = { editing = null },
            onConfirm = { name, price, cycle, _, category ->
                onEdit(s, name, price, cycle, category)
                editing = null
            },
        )
    }
    toDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Eliminar ${s.name}?") },
            confirmButton = { TextButton(onClick = { onDelete(s); toDelete = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SummaryCard(items: List<Subscription>, activeCount: Int, now: Long) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(24.dp)) {
            Text("Gasto mensual", style = MaterialTheme.typography.labelLarge)
            Text(money(monthlySpending(items)), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            Text(
                "${money(yearlySpending(items))} por año · $activeCount suscripciones",
                style = MaterialTheme.typography.bodyMedium,
            )
            val soon = upcomingCharges(items, now, 7)
            if (soon > 0) {
                Text(
                    "Próximos 7 días: ${money(soon)}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun CategoryBreakdown(data: List<Pair<Category, Double>>) {
    val total = data.sumOf { it.second }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Por categoría", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape)) {
                data.forEach { (c, v) ->
                    Box(Modifier.weight(v.toFloat().coerceAtLeast(0.001f)).height(12.dp).background(categoryColors.getValue(c)))
                }
            }
            data.forEach { (c, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(categoryColors.getValue(c)))
                    Text(c.label, Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${money(v)} · ${(v / total * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionCard(sub: Subscription, subtitle: String, urgent: Boolean, actions: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(categoryColors.getValue(sub.category)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    sub.name.take(1).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(sub.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${sub.category.label} · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(sub.nextRenewal(System.currentTimeMillis())))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            actions()
        }
    }
}

@Composable
private fun SubscriptionDialog(
    initial: Subscription?,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, BillingCycle, Int?, Category) -> Unit,
) {
    val editing = initial != null
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var price by remember { mutableStateOf(initial?.price?.toString() ?: "") }
    var cycle by remember { mutableStateOf(initial?.cycle ?: BillingCycle.MONTHLY) }
    var category by remember { mutableStateOf(initial?.category ?: Category.OTHER) }
    var isTrial by remember { mutableStateOf(initial?.isTrial ?: false) }
    var trialDays by remember { mutableStateOf("7") }

    val priceValue = price.replace(',', '.').toDoubleOrNull()
    val daysValue = trialDays.toIntOrNull()
    val valid = name.isNotBlank() && priceValue != null && priceValue >= 0 && (!isTrial || editing || (daysValue != null && daysValue >= 0))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    editing -> "Editar"
                    isTrial -> "Nuevo período de prueba"
                    else -> "Nueva suscripción"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!editing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !isTrial, onClick = { isTrial = false }, label = { Text("Suscripción") })
                        FilterChip(selected = isTrial, onClick = { isTrial = true }, label = { Text("En prueba") })
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Nombre (ej. Netflix)") }, singleLine = true)
                OutlinedTextField(
                    price,
                    { price = it },
                    label = { Text(if (isTrial) "Precio al terminar la prueba" else "Precio") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = cycle == BillingCycle.MONTHLY, onClick = { cycle = BillingCycle.MONTHLY }, label = { Text("Mensual") })
                    FilterChip(selected = cycle == BillingCycle.YEARLY, onClick = { cycle = BillingCycle.YEARLY }, label = { Text("Anual") })
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
                    }
                }
                if (isTrial && !editing) {
                    OutlinedTextField(
                        trialDays,
                        { trialDays = it },
                        label = { Text("Días de prueba restantes") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(name.trim(), priceValue!!, cycle, if (isTrial) daysValue else null, category) },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

fun trialEndFromDays(nowMillis: Long, days: Int): Long = nowMillis + TimeUnit.DAYS.toMillis(days.toLong())
