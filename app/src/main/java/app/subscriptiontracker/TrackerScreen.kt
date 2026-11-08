package app.subscriptiontracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import java.util.Locale
import java.util.concurrent.TimeUnit

private fun money(v: Double) = String.format(Locale.getDefault(), "$%.2f", v)

@Composable
fun TrackerScreen(
    items: List<Subscription>,
    onAdd: (name: String, price: Double, cycle: BillingCycle, trialDays: Int?) -> Unit,
    onDelete: (Subscription) -> Unit,
    onConvertTrial: (Subscription) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val active = items.filter { !it.isTrial }
    val trials = items.filter { it.isTrial }.sortedBy { it.trialEndMillis }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(Modifier.padding(top = 24.dp)) {
                Text("Gasto mensual", style = MaterialTheme.typography.labelLarge)
                Text(money(monthlySpending(items)), style = MaterialTheme.typography.displaySmall)
                Text(
                    "${money(yearlySpending(items))} por año · ${active.size} suscripciones",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { showAdd = true }, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Agregar")
                }
            }
        }

        if (trials.isNotEmpty()) {
            item { Text("Períodos de prueba", style = MaterialTheme.typography.titleMedium) }
            items(trials, key = { it.id }) { s ->
                val days = s.trialDaysLeft(now) ?: 0
                val status = when {
                    days < 0 -> "Venció hace ${-days} d: cancelalo ya"
                    days == 0L -> "Termina hoy: cancelalo ya"
                    days == 1L -> "Queda 1 día"
                    else -> "Quedan $days días"
                }
                SubscriptionCard(
                    title = s.name,
                    subtitle = "$status · después ${money(s.price)}/${cycleLabel(s.cycle)}",
                    actions = {
                        TextButton(onClick = { onConvertTrial(s) }) { Text("Me quedo") }
                        TextButton(onClick = { onDelete(s) }) { Text("Cancelé") }
                    },
                )
            }
        }

        item { Text("Suscripciones", style = MaterialTheme.typography.titleMedium) }
        if (active.isEmpty()) {
            item { Text("Todavía no agregaste ninguna.") }
        }
        items(active, key = { it.id }) { s ->
            SubscriptionCard(
                title = s.name,
                subtitle = "${money(s.price)}/${cycleLabel(s.cycle)}",
                actions = { TextButton(onClick = { onDelete(s) }) { Text("Eliminar") } },
            )
        }
    }

    if (showAdd) {
        AddDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, price, cycle, trialDays ->
                onAdd(name, price, cycle, trialDays)
                showAdd = false
            },
        )
    }
}

private fun cycleLabel(c: BillingCycle) = if (c == BillingCycle.MONTHLY) "mes" else "año"

@Composable
private fun SubscriptionCard(title: String, subtitle: String, actions: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            actions()
        }
    }
}

@Composable
private fun AddDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double, BillingCycle, Int?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var cycle by remember { mutableStateOf(BillingCycle.MONTHLY) }
    var isTrial by remember { mutableStateOf(false) }
    var trialDays by remember { mutableStateOf("7") }

    val priceValue = price.replace(',', '.').toDoubleOrNull()
    val daysValue = trialDays.toIntOrNull()
    val valid = name.isNotBlank() && priceValue != null && priceValue >= 0 && (!isTrial || (daysValue != null && daysValue >= 0))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isTrial) "Nuevo período de prueba" else "Nueva suscripción") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !isTrial, onClick = { isTrial = false }, label = { Text("Suscripción") })
                    FilterChip(selected = isTrial, onClick = { isTrial = true }, label = { Text("En prueba") })
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
                if (isTrial) {
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
                onClick = { onConfirm(name.trim(), priceValue!!, cycle, if (isTrial) daysValue else null) },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

fun trialEndFromDays(nowMillis: Long, days: Int): Long = nowMillis + TimeUnit.DAYS.toMillis(days.toLong())
