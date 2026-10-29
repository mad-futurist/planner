package app.planner.presentation.settings

import app.planner.domain.model.OutboxOperation
import java.io.Writer

data class SettingsActions(
    val setPreference: (String, String)->Unit, val connect: (String, String)->Unit, val sync: ()->Unit,
    val export: (() -> Writer?) -> Unit, val clear: ()->Unit,
    val retry: (OutboxOperation)->Unit, val keepLocal: (OutboxOperation)->Unit, val report: (String)->Unit,
    val account: (String, String, String, String)->Unit, val google: (String, String?)->Unit,
    val signOut: ()->Unit, val deleteAccount: (String)->Unit,
    val loadContribution: ()->Unit, val contribution: (Boolean?)->Unit,
)
