package com.cardash.integration

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

/**
 * Root detection and privileged operations helper
 * Based on Aptoide's approach for Chinese head units with su binary
 */
object RootHelper {

    private const val TAG = "RootHelper"

    /**
     * Check if device is rooted by detecting su binary
     */
    fun isRootAvailable(): Boolean {
        // Check common su binary locations
        val suPaths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/su",
            "/su/bin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su"
        )

        for (path in suPaths) {
            if (File(path).exists()) {
                Log.d(TAG, "Found su binary at: $path")
                return true
            }
        }

        // Check PATH environment variable
        try {
            val pathEnv = System.getenv("PATH") ?: ""
            for (pathDir in pathEnv.split(":")) {
                val suFile = File(pathDir, "su")
                if (suFile.exists()) {
                    Log.d(TAG, "Found su in PATH at: ${suFile.absolutePath}")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking PATH: ${e.message}")
        }

        return false
    }

    /**
     * Silently check if root works (no prompts on debug builds)
     * On Chinese head units with debug builds, su is available without authorization
     */
    private fun canExecuteRoot(): Boolean {
        // Don't use "id" command as it may trigger prompts on some devices
        // Just try to execute a harmless command and see if it works
        val result = executeCommandSilent("echo root_test")
        return result?.contains("root_test") == true
    }

    /**
     * Silently execute command (no verbose logging, used for testing)
     */
    private fun executeCommandSilent(command: String): String? {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            val inputStream = BufferedReader(InputStreamReader(process.inputStream))

            outputStream.writeBytes("$command\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()

            val output = StringBuilder()
            var line: String?
            while (inputStream.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            process.waitFor()
            return if (process.exitValue() == 0) output.toString() else null
        } catch (e: Exception) {
            return null
        } finally {
            process?.destroy()
        }
    }

    /**
     * Execute a command with root privileges
     */
    private fun executeCommand(command: String): String? {
        var process: Process? = null
        try {
            Log.d(TAG, "Executing root command: $command")
            process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            val inputStream = BufferedReader(InputStreamReader(process.inputStream))
            val errorStream = BufferedReader(InputStreamReader(process.errorStream))

            // Send command
            outputStream.writeBytes("$command\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()

            // Read output
            val output = StringBuilder()
            var line: String?
            while (inputStream.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            // Read errors
            val errors = StringBuilder()
            while (errorStream.readLine().also { line = it } != null) {
                errors.append(line).append("\n")
            }

            process.waitFor()

            if (errors.isNotEmpty()) {
                Log.w(TAG, "Command stderr: $errors")
            }

            return if (process.exitValue() == 0) {
                Log.d(TAG, "Command success: ${output.toString().trim()}")
                output.toString()
            } else {
                Log.e(TAG, "Command failed with exit code: ${process.exitValue()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed: ${e.message}")
            return null
        } finally {
            process?.destroy()
        }
    }

    /**
     * Force enable WiFi using root (bypasses Android 10+ restrictions)
     */
    fun forceEnableWifi(): Boolean {
        Log.d(TAG, "Attempting to force enable WiFi with root")
        val result = executeCommand("svc wifi enable")
        return result != null
    }

    /**
     * Force disable WiFi using root
     */
    fun forceDisableWifi(): Boolean {
        Log.d(TAG, "Attempting to force disable WiFi with root")
        val result = executeCommand("svc wifi disable")
        return result != null
    }

    /**
     * Disable WiFi hotspot/SoftAP using root
     * This is critical for Chinese head units that block WiFi when hotspot is on
     */
    fun disableHotspot(): Boolean {
        Log.d(TAG, "Attempting to disable hotspot with root")

        // Try multiple methods (different Android versions use different commands)
        val methods = arrayOf(
            "svc wifi disable-ap",
            "svc wifi disable-softap",
            "cmd wifi stop-softap",
            "settings put global wifi_ap_state 11"  // 11 = disabled
        )

        for (method in methods) {
            Log.d(TAG, "Trying method: $method")
            val result = executeCommand(method)
            if (result != null) {
                Log.d(TAG, "Hotspot disabled successfully with: $method")
                return true
            }
        }

        Log.w(TAG, "All hotspot disable methods failed")
        return false
    }

    /**
     * Disable Bluetooth using root
     * Prevents CarPlay from auto-connecting
     */
    fun disableBluetooth(): Boolean {
        Log.d(TAG, "Attempting to disable Bluetooth with root")
        val result = executeCommand("svc bluetooth disable")
        return result != null
    }

    /**
     * Enable Bluetooth using root
     */
    fun enableBluetooth(): Boolean {
        Log.d(TAG, "Attempting to enable Bluetooth with root")
        val result = executeCommand("svc bluetooth enable")
        return result != null
    }

    /**
     * Connect to a specific WiFi network using root
     * Bypasses all user prompts and restrictions
     */
    fun connectToWifi(ssid: String, password: String): Boolean {
        Log.d(TAG, "Attempting to connect to WiFi: $ssid with root")

        // Use wpa_cli to connect
        val commands = """
            wpa_cli -i wlan0 add_network
            wpa_cli -i wlan0 set_network 0 ssid '"$ssid"'
            wpa_cli -i wlan0 set_network 0 psk '"$password"'
            wpa_cli -i wlan0 enable_network 0
            wpa_cli -i wlan0 select_network 0
            wpa_cli -i wlan0 reconnect
        """.trimIndent()

        val result = executeCommand(commands)
        return result != null
    }

    /**
     * Try multiple passwords for a ZS- device
     * Automatically attempts connection with fallback passwords
     * Returns true on first successful connection
     */
    fun tryZsPasswords(ssid: String, passwords: List<String>): Boolean {
        Log.d(TAG, "Trying ${passwords.size} passwords for $ssid")

        for ((index, password) in passwords.withIndex()) {
            Log.d(TAG, "Attempt ${index + 1}/${passwords.size} for $ssid")

            if (connectToWifi(ssid, password)) {
                Log.d(TAG, "Successfully connected to $ssid with password attempt ${index + 1}")
                return true
            }

            // Wait between attempts
            try {
                Thread.sleep(2000)
            } catch (e: InterruptedException) {
                return false
            }
        }

        Log.w(TAG, "All password attempts failed for $ssid")
        return false
    }

    /**
     * Scan for ZS- networks and return first one found
     * Returns null if no ZS- networks found
     */
    fun scanForZsNetworks(): String? {
        Log.d(TAG, "Scanning for ZS- networks")

        // Trigger scan
        executeCommand("wpa_cli -i wlan0 scan")

        // Wait for scan to complete
        try {
            Thread.sleep(3000)
        } catch (e: InterruptedException) {
            return null
        }

        // Get scan results
        val scanResult = executeCommand("wpa_cli -i wlan0 scan_results") ?: return null

        // Parse results and find ZS- networks
        val lines = scanResult.lines()
        for (line in lines) {
            if (line.contains("ZS", ignoreCase = true)) {
                // SSID is the last field in scan_results
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 5) {
                    val ssid = parts.drop(4).joinToString(" ").trim()
                    Log.d(TAG, "Found ZS network: $ssid")
                    return ssid
                }
            }
        }

        Log.d(TAG, "No ZS- networks found in scan")
        return null
    }

    /**
     * Grant a permission to this app using root
     */
    fun grantPermission(packageName: String, permission: String): Boolean {
        Log.d(TAG, "Granting permission $permission to $packageName")
        val result = executeCommand("pm grant $packageName $permission")
        return result != null
    }

    /**
     * Check if we have root and can execute commands
     * Silent operation - no prompts (works on Chinese head units with debug builds)
     */
    fun canUseRoot(): Boolean {
        if (!isRootAvailable()) {
            Log.d(TAG, "No su binary found")
            return false
        }

        // Silently test if we can actually execute root commands
        val canExec = canExecuteRoot()
        if (canExec) {
            Log.d(TAG, "Root commands executable (debug build or pre-authorized)")
        } else {
            Log.d(TAG, "Root binary found but not executable")
        }
        return canExec
    }
}
