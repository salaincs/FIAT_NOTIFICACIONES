package za.co.mitchwongho.example.esp32.alerts.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import android.widget.Toast
import za.co.mitchwongho.example.esp32.alerts.R
import java.util.regex.Pattern

/**
 *
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        val PREF_KEY_RUN_AS_A_SERVICE = "pref_as_bg_service"
        val PREF_KEY_REMOTE_MAC_ADDRESS = "pref_remote_mac_address"
        val PREF_KEY_START_AT_BOOT = "pref_start_at_boot"
        val PREF_KEY_FLIP_DISPLAY_VERTICALLY = "pref_flip_vertically"
        val MAC_PATTERN = Pattern.compile("^([A-F0-9]{2}[:]?){5}[A-F0-9]{2}$")

        class SettingsFragment : PreferenceFragmentCompat() {

            override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
                setPreferencesFromResource(R.xml.preferences, rootKey)
                //
                // apply persisted value
                val sharedPref = PreferenceManager.getDefaultSharedPreferences(requireContext())
                setRemoteMACAddressPrefSummary(sharedPref.getString(PREF_KEY_REMOTE_MAC_ADDRESS, "00:00:00:00:00:00") ?: "00:00:00:00:00:00")
                //
                // validate updates and apply is valid
                findPreference<Preference>(PREF_KEY_REMOTE_MAC_ADDRESS)?.setOnPreferenceChangeListener({ preference: Preference?, value: Any? ->
                    val mac = (value as String).trim()
                    if (MAC_PATTERN.matcher(mac).find()) {
                        setRemoteMACAddressPrefSummary(mac)
                        true
                    } else {
                        Toast.makeText(requireContext(), R.string.mac_format_error, Toast.LENGTH_LONG).show()
                        false
                    }
                })
            }

            fun setRemoteMACAddressPrefSummary(summary: String) {
                val pref = findPreference<Preference>(PREF_KEY_REMOTE_MAC_ADDRESS)
                pref?.summary = summary
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, SettingsActivity.Companion.SettingsFragment())
                .commit()
    }

    override fun onStart() {
        super.onStart()
        stopService(Intent(this, ForegroundService::class.java))
    }
}