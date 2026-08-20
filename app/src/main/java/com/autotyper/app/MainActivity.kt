package com.autotyper.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val refresh = mutableStateOf(0)
    private val crashText = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        crashText.value = CrashLog.read(this)

        if (Settings.canDrawOverlays(this)) {
            try {
                startService(Intent(this, FloatingPanelService::class.java))
            } catch (_: Exception) {
            }
        }
        requestNotificationPermission()

        setContent {
            AutoTyperTheme {
                val tick = refresh.value // read so we recompose on resume
                HomeScreen(tick)

                crashText.value?.let { msg ->
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text("The app crashed last time", fontWeight = FontWeight.Bold) },
                        text = { Text(msg.take(1600), fontSize = 11.sp) },
                        confirmButton = {
                            TextButton(onClick = {
                                CrashLog.clear(this)
                                crashText.value = null
                            }) {
                                Text("Got it")
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh.value++
    }

    // ---------- permission helpers ----------

    private fun imeComponent(): String = "$packageName/.AutoTyperIMEService"

    private fun isImeEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_INPUT_METHODS
        ) ?: return false
        return enabled.split(':').any { it == imeComponent() }
    }

    private fun isImeActive(): Boolean {
        val def = Settings.Secure.getString(
            contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
        )
        return def == imeComponent()
    }

    private fun canOverlay(): Boolean = Settings.canDrawOverlays(this)

    private fun ignoringBattery(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermissionCompat(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun checkSelfPermissionCompat(perm: String): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(this, perm) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED

    // ---------- setup actions ----------

    private fun openKeyboardSettings() {
        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }

    private fun openKeyboardPicker() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }

    private fun openOverlaySettings() {
        val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivity(i)
    }

    private fun openBatterySettings() {
        try {
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            startActivity(i)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun startTyping(text: String, wpm: Int, humanity: Float) {
        Prefs.setLastText(this, text)
        Prefs.setWpm(this, wpm)
        Prefs.setHumanity(this, humanity)
        if (!isImeEnabled()) {
            Toast.makeText(this, "Enable the AutoTyper keyboard first (Setup below)", Toast.LENGTH_LONG).show()
            return
        }
        TypingSession.start(text, TypeConfig(wpm, humanity))
        if (isImeActive()) {
            Toast.makeText(this, "Now tap the field you want to type into", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Pick the AutoTyper keyboard, then tap the field", Toast.LENGTH_LONG).show()
            openKeyboardPicker()
        }
    }

    // ---------- UI ----------

    @Composable
    private fun HomeScreen(@Suppress("UNUSED_PARAMETER") tick: Int) {
        val ctx = LocalContext.current
        var text by remember { mutableStateOf(Prefs.getLastText(ctx)) }
        var wpm by remember { mutableStateOf(Prefs.getWpm(ctx).toFloat()) }
        var humanity by remember { mutableStateOf(Prefs.getHumanity(ctx)) }
        var snippets by remember { mutableStateOf(Prefs.getSnippets(ctx)) }

        val typing = TypingSession.running
        val paused = TypingSession.paused

        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("AutoTyper", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("type like a human", color = Color(0xFF999999), fontSize = 13.sp)
                }
                StatusChip(active = isImeActive(), label = if (isImeActive()) "Keyboard active" else "Keyboard inactive")
            }

            Spacer(Modifier.height(20.dp))

            SetupCard()

            Spacer(Modifier.height(20.dp))

            // Text input
            SectionTitle("Text to type")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                placeholder = { Text("Paste or type the text here…", color = Color(0xFF666666)) },
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color(0xFF333333),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedContainerColor = Color(0xFF0C0C0C),
                    unfocusedContainerColor = Color(0xFF0C0C0C)
                )
            )
            Spacer(Modifier.height(6.dp))
            Row {
                TextButton(onClick = { text = "" }) {
                    Text("Clear", color = Color(0xFF999999))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val s = clip.primaryClip?.getItemAt(0)?.text?.toString()
                    if (!s.isNullOrBlank()) text = s
                    else Toast.makeText(ctx, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Paste from clipboard", color = Color.White)
                }
                TextButton(onClick = {
                    if (text.isNotBlank()) {
                        Prefs.addSnippet(ctx, text)
                        snippets = Prefs.getSnippets(ctx)
                        Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Save", color = Color.White)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Speed slider
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionTitle("Speed")
                Text("${wpm.toInt()} WPM", color = Color(0xFF999999), fontSize = 13.sp)
            }
            Slider(
                value = wpm,
                onValueChange = { wpm = it },
                valueRange = 30f..140f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color(0xFF333333)
                )
            )

            Spacer(Modifier.height(8.dp))

            // Humanity slider
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionTitle("Humanity")
                Text(humanityLabel(humanity), color = Color(0xFF999999), fontSize = 13.sp)
            }
            Slider(
                value = humanity,
                onValueChange = { humanity = it },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color(0xFF333333)
                )
            )

            Spacer(Modifier.height(16.dp))

            // Start button
            Button(
                onClick = {
                    when {
                        typing && paused -> TypingSession.resume()
                        typing -> TypingSession.pause()
                        else -> startTyping(text, wpm.toInt(), humanity)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    when {
                        typing && paused -> "RESUME"
                        typing -> "PAUSE"
                        else -> "START TYPING"
                    },
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            if (typing) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { TypingSession.stop() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1A1A1A),
                        contentColor = Color(0xFFBBBBBB)
                    )
                ) {
                    Text("STOP", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            // Snippets
            if (snippets.isNotEmpty()) {
                SectionTitle("Saved snippets")
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    snippets.forEach { s ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF101010), RoundedCornerShape(12.dp))
                                .clickable { text = s }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                s.replace('\n', ' '),
                                color = Color(0xFFDDDDDD),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                Prefs.removeSnippet(ctx, s)
                                snippets = Prefs.getSnippets(ctx)
                            }) {
                                Text("✕", color = Color(0xFF777777))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    @Composable
    private fun SetupCard() {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF0C0C0C), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text("Setup", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))

            SetupRow("Enable keyboard", isImeEnabled(), "Enable") { openKeyboardSettings() }
            HorizontalDivider(color = Color(0xFF1A1A1A))
            SetupRow("Switch to AutoTyper", isImeActive(), "Switch") { openKeyboardPicker() }
            HorizontalDivider(color = Color(0xFF1A1A1A))
            SetupRow("Allow overlay", canOverlay(), "Allow") { openOverlaySettings() }
            HorizontalDivider(color = Color(0xFF1A1A1A))
            SetupRow("Ignore battery saving", ignoringBattery(), "Allow") { openBatterySettings() }
        }
    }

    @Composable
    private fun SetupRow(title: String, done: Boolean, actionLabel: String, onClick: () -> Unit) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .background(
                        if (done) Color.White else Color(0xFF222222),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (done) "✓" else "!",
                    color = if (done) Color.Black else Color(0xFF888888),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(title, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (!done) {
                TextButton(onClick = onClick) {
                    Text(actionLabel, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    private fun StatusChip(active: Boolean, label: String) {
        Box(
            Modifier
                .background(
                    if (active) Color.White else Color(0xFF1A1A1A),
                    RoundedCornerShape(50)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                label,
                color = if (active) Color.Black else Color(0xFF999999),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    @Composable
    private fun SectionTitle(text: String) {
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }

    private fun humanityLabel(v: Float): String = when {
        v < 0.2f -> "Robot"
        v < 0.45f -> "Clean"
        v < 0.75f -> "Natural"
        else -> "Messy human"
    }
}
