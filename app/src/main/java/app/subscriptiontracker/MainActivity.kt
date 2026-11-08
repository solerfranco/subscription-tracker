package app.subscriptiontracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val store by lazy { SubscriptionStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var update by remember { mutableStateOf<UpdateInfo?>(null) }
                var items by remember { mutableStateOf(store.load()) }
                val saveItems: (List<Subscription>) -> Unit = { items = it; store.save(it) }

                // Al abrir la app, busca una versión nueva. Sin red o con error, no molesta.
                LaunchedEffect(Unit) {
                    update = withContext(Dispatchers.IO) {
                        runCatching { UpdateChecker.check(BuildConfig.UPDATE_REPO, BuildConfig.VERSION_NAME) }.getOrNull()
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    TrackerScreen(
                        items = items,
                        onAdd = { name, price, cycle, trialDays ->
                            val trialEnd = trialDays?.let { trialEndFromDays(System.currentTimeMillis(), it) }
                            saveItems(items + Subscription(System.currentTimeMillis(), name, price, cycle, trialEnd))
                        },
                        onDelete = { s -> saveItems(items.filter { it.id != s.id }) },
                        onConvertTrial = { s ->
                            saveItems(items.map { if (it.id == s.id) it.copy(trialEndMillis = null) else it })
                        },
                        modifier = Modifier.statusBarsPadding(),
                    )
                }

                update?.let { info ->
                    UpdateDialog(info, onDismiss = { update = null })
                }
            }
        }
    }
}

@Composable
private fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (progress == null) onDismiss() },
        title = { Text("Hay una versión nueva (${info.version})") },
        text = {
            Column {
                Text("¿Actualizar ahora?")
                progress?.let { p ->
                    LinearProgressIndicator(
                        progress = { p / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
                error?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = progress == null,
                onClick = {
                    if (!UpdateInstaller.canInstall(context)) {
                        error = "Permitile a la app instalar actualizaciones y volvé a tocar Actualizar."
                        UpdateInstaller.openInstallPermissionSettings(context)
                        return@TextButton
                    }
                    error = null
                    progress = 0
                    scope.launch {
                        try {
                            val apk = withContext(Dispatchers.IO) {
                                UpdateInstaller.download(context, info.apkUrl, info.version) { p ->
                                    scope.launch(Dispatchers.Main) { progress = p }
                                }
                            }
                            progress = null
                            UpdateInstaller.install(context, apk)
                        } catch (e: Exception) {
                            progress = null
                            error = "No pude descargar la actualización. Probá de nuevo más tarde."
                        }
                    }
                },
            ) { Text("Actualizar") }
        },
        dismissButton = {
            TextButton(enabled = progress == null, onClick = onDismiss) { Text("Después") }
        },
    )
}
