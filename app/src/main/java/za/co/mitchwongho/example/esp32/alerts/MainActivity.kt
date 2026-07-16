package za.co.mitchwongho.example.esp32.alerts

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.PreferenceManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import timber.log.Timber
import za.co.mitchwongho.example.esp32.alerts.app.ForegroundService
import za.co.mitchwongho.example.esp32.alerts.app.MainApplication
import za.co.mitchwongho.example.esp32.alerts.app.SettingsActivity
import java.util.*
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {

    lateinit var fab: FloatingActionButton
    lateinit var btnOpen: MaterialButton
    lateinit var btnClose: MaterialButton
    lateinit var btnTrunk: MaterialButton
    lateinit var menu: Menu
    var alertDialog: AlertDialog? = null

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Si hay error o cancela, cerramos la app por seguridad
                    finish()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Toast.makeText(applicationContext, "Identidad verificada", Toast.LENGTH_SHORT).show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Acceso Seguro - Notificaciones BRAVO")
            .setSubtitle("Usa tu huella o PIN para entrar")
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "Permissions required for Bluetooth and Notifications", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE)
        }

        val toRequest = permissions.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(toRequest.toTypedArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAndRequestPermissions()

        fab = findViewById(R.id.fab)
        btnOpen = findViewById(R.id.btn_open)
        btnClose = findViewById(R.id.btn_close)
        btnTrunk = findViewById(R.id.btn_trunk)

        btnOpen.setOnClickListener {
            sendRemoteCommand("ACTION_DOORS_OPEN", "Abriendo puertas...")
        }
        btnClose.setOnClickListener {
            sendRemoteCommand("ACTION_DOORS_CLOSE", "Cerrando puertas...")
        }
        btnTrunk.setOnClickListener {
            sendRemoteCommand("ACTION_DOORS_TRUNK", "Abriendo baúl...")
        }

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

        fab.setOnClickListener { showAppChooserDialog() }

        val isSecurityEnabled = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("pref_security_lock", false)

        if (isSecurityEnabled) {
            showBiometricPrompt()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        this.menu = menu
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_prefs -> {
                showPreferences()
                true
            }
            R.id.menu_item_kill -> {
                stopService(Intent(this, ForegroundService::class.java))
                item.setVisible(false)
                menu.findItem(R.id.menu_item_start)?.setVisible(true)
                true
            }
            R.id.menu_item_start -> {
                val intent = Intent(this, ForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                item.setVisible(false)
                menu.findItem(R.id.menu_item_kill)?.setVisible(true)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    }

    override fun onStart() {
        super.onStart()
        // Solo iniciamos el servicio si ya tenemos los permisos
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(this, ForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
        Timber.w("onStart - Waiting for permissions or starting service")
    }

    override fun onDestroy() {
        super.onDestroy()
        // stop the service
        val isRunAsAService = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(SettingsActivity.PREF_KEY_RUN_AS_A_SERVICE, false)
        Timber.w("onDestroy {isService=$isRunAsAService}")
        // Removed stopService here to allow it to run in background
    }

    private fun showAppChooserDialog() {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(BuildConfig.APPLICATION_ID)
        if (!enabled) {
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                .setTitle(R.string.choose_app)
                .setMessage("Parece que primero debes otorgar acceso a las notificaciones. ¿Deseas continuar?")
                .setNegativeButton(android.R.string.no, null)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    } else {
                        startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    }
                }
            builder.create().show()
            return
        }

        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val appList = installedApps.map {
            AppInfo(packageManager.getApplicationLabel(it).toString(), it.packageName)
        }.sortedBy { it.name }

        val prefsAllowedPackages = MainApplication.sharedPrefs.getStringSet(MainApplication.PREFS_KEY_ALLOWED_PACKAGES, mutableSetOf()) ?: mutableSetOf()
        val selectedPackages = prefsAllowedPackages.toMutableSet()

        val dialogView = layoutInflater.inflate(R.layout.dialog_app_chooser, null)
        val searchView = dialogView.findViewById<SearchView>(R.id.search_view)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recycler_view)

        val adapter = AppAdapter(appList, selectedPackages)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter(newText ?: "")
                return true
            }
        })

        AlertDialog.Builder(this)
            .setTitle(R.string.choose_app)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                MainApplication.sharedPrefs.edit().putStringSet(MainApplication.PREFS_KEY_ALLOWED_PACKAGES, selectedPackages).apply()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    data class AppInfo(val name: String, val packageName: String)

    inner class AppAdapter(private val allApps: List<AppInfo>, private val selectedPackages: MutableSet<String>) :
        RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        private var filteredApps = allApps

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.app_name)
            val checkBox: CheckBox = view.findViewById(R.id.checkbox)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_app_choice, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = filteredApps[position]
            holder.name.text = app.name
            holder.checkBox.isChecked = selectedPackages.contains(app.packageName)
            holder.itemView.setOnClickListener {
                if (selectedPackages.contains(app.packageName)) {
                    selectedPackages.remove(app.packageName)
                } else {
                    selectedPackages.add(app.packageName)
                }
                notifyItemChanged(position)
            }
        }

        override fun getItemCount(): Int = filteredApps.size

        fun filter(query: String) {
            filteredApps = if (query.isEmpty()) {
                allApps
            } else {
                allApps.filter { it.name.contains(query, ignoreCase = true) }
            }
            notifyDataSetChanged()
        }
    }

    private fun sendRemoteCommand(action: String, toastMsg: String) {
        val intent = Intent(action)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
    }

    fun showPreferences() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
}
