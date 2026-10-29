package app.planner.presentation.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.planner.presentation.settings.SettingsActions
import app.planner.presentation.settings.SettingsUiState

@Composable internal fun AccountControls(state: SettingsUiState, actions: SettingsActions, server: String) {
    val context = LocalContext.current
    val metadata = state.metadata
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords deliberately stay out of saved state and persistent preferences.
    var password by remember { mutableStateOf("") }
    val busy = "account" in state.busy
    val feedback = state.operations.accountFeedback
    LaunchedEffect(feedback) { if (feedback != null) password = "" }
    var deleting by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf("") }
    fun account(action: String) { actions.account(server, action, email, password) }
    fun google(link: Boolean) { actions.google(server, password.takeIf { link }) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Account", style = MaterialTheme.typography.titleMedium)
        Text("Sign in to the same account on each device. Notes stay private until you choose Cloud AI.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("account-email"))
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().testTag("account-password"))
        Row {
            TextButton({ account("login") }, enabled = !busy && server.isNotBlank() && email.isNotBlank() && password.isNotEmpty(), modifier = Modifier.testTag("account-login")) { Text("Sign in") }
            TextButton({ account("register") }, enabled = !busy && server.isNotBlank() && email.isNotBlank() && password.length in 12..128) { Text("Create account") }
        }
        Text("New accounts: use at least 12 characters and confirm the email before signing in.", style = MaterialTheme.typography.bodySmall)
        TextButton({ account("forgot-password") }, enabled = !busy && server.isNotBlank() && email.isNotBlank()) { Text("Forgot password / set a password") }
        TextButton({ google(false) }, enabled = !busy && server.isNotBlank(), modifier = Modifier.testTag("account-google")) { Text("Continue with Google") }
        if (metadata["owner"] != null) {
            TextButton({ google(true) }, enabled = !busy && password.isNotEmpty()) { Text("Link Google · confirm current password") }
            TextButton({ actions.signOut() }, enabled = !busy) { Text("Sign out") }
            TextButton({ confirmation = ""; deleting = true }, enabled = !busy) { Text("Delete server account…") }
        }
        feedback?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete your server account?") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("This permanently deletes account data on the server and revokes all device sessions. Local notes remain available for export; clear this device separately before using a different account. Provider copies and backup retention follow your service's policy.")
            OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Type DELETE") })
            OutlinedTextField(password, { password = it }, label = { Text("Current password") }, visualTransformation = PasswordVisualTransformation())
        }
    }, confirmButton = { TextButton({ actions.deleteAccount(password); deleting = false }, enabled = confirmation == "DELETE" && password.isNotEmpty() && !busy) { Text("Delete account") } }, dismissButton = { TextButton({ deleting = false }) { Text("Cancel") } })
}
