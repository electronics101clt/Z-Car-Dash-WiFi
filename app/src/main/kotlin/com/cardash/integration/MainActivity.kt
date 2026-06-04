package com.cardash.integration

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.content.Intent
import android.net.*
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var statusText: TextView
    private lateinit var webView: WebView
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private lateinit var credentials: NetworkCredentials

    private val TAG = "WifiSelector"
    private val ESP32_URL = "http://192.168.4.1"

    private var bluetoothWasEnabled = false
    private var hotspotWasEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        statusText = findViewById(R.id.statusText)
        webView = findViewById(R.id.webView)
        credentials = NetworkCredentials(this)

        setupWebView()
        setupStatusTextLongPress()
        checkPermissions()
    }

    private fun setupStatusTextLongPress() {
        statusText.setOnLongClickListener {
            showCredentialsDialog()
            true
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Hide status immediately when page starts loading
                statusText.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                statusText.visibility = View.GONE
            }

            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                super.onReceivedError(view, request, error)
                updateStatus("Connection error")
            }
        }

        // Start hidden
        webView.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        forceWifiOn()
    }

    @Suppress("DEPRECATION")
    private fun forceWifiOn() {
        // Always try to force WiFi on first
        if (!wifiManager.isWifiEnabled) {
            Log.d(TAG, "WiFi is off, attempting to enable")

            // Try to kill SoftAP and Bluetooth first
            killSoftAp()
            killBluetooth()

            // Try setWifiEnabled (works on Android 9 and below)
            val success = try {
                wifiManager.setWifiEnabled(true)
            } catch (e: Exception) {
                Log.e(TAG, "setWifiEnabled exception: ${e.message}")
                false
            }

            if (success) {
                Log.d(TAG, "setWifiEnabled returned true, waiting...")
                statusText.postDelayed({ tryBind() }, 1500)
            } else {
                Log.d(TAG, "setWifiEnabled failed")
                // On Android 10+, use Settings panel
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Auto-open panel silently
                    startActivity(Intent(Settings.Panel.ACTION_WIFI))
                } else {
                    tryBind()
                }
            }
        } else {
            tryBind()
        }
    }

    @Suppress("DEPRECATION")
    private fun killSoftAp() {
        try {
            // Save hotspot state before killing
            val apState = getWifiApState()
            hotspotWasEnabled = (apState == 13 || apState == 12) // AP_STATE_ENABLED or AP_STATE_ENABLING

            // Try multiple reflection methods to disable SoftAP
            val setWifiApEnabled = wifiManager.javaClass.getMethod(
                "setWifiApEnabled",
                android.net.wifi.WifiConfiguration::class.java,
                Boolean::class.javaPrimitiveType
            )
            setWifiApEnabled.invoke(wifiManager, null, false)
            Log.d(TAG, "killSoftAp: setWifiApEnabled(null, false) called (was enabled: $hotspotWasEnabled)")
        } catch (e: Exception) {
            Log.d(TAG, "killSoftAp failed: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun killBluetooth() {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter != null) {
                // Save Bluetooth state before killing
                bluetoothWasEnabled = bluetoothAdapter.isEnabled

                if (bluetoothAdapter.isEnabled) {
                    val success = bluetoothAdapter.disable()
                    Log.d(TAG, "killBluetooth: disable() returned $success (was enabled: $bluetoothWasEnabled)")
                    if (!success) {
                        Log.d(TAG, "killBluetooth: automatic disable failed")
                    }
                } else {
                    Log.d(TAG, "killBluetooth: Bluetooth already off")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "killBluetooth failed: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        super.onDestroy()

        // Unregister network callback for API 29+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            networkCallback?.let {
                try {
                    connectivityManager.unregisterNetworkCallback(it)
                    Log.d(TAG, "Unregistered network callback")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to unregister callback", e)
                }
            }
        }

        // Release WiFi binding for all versions
        try {
            connectivityManager.bindProcessToNetwork(null)
            Log.d(TAG, "Released WiFi binding")
        } catch (e: Exception) {}

        // Restore Bluetooth to original state
        if (bluetoothWasEnabled) {
            try {
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                bluetoothAdapter?.enable()
                Log.d(TAG, "Restored Bluetooth (was enabled)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore Bluetooth: ${e.message}")
            }
        }

        // Restore hotspot to original state
        if (hotspotWasEnabled) {
            try {
                val setWifiApEnabled = wifiManager.javaClass.getMethod(
                    "setWifiApEnabled",
                    android.net.wifi.WifiConfiguration::class.java,
                    Boolean::class.javaPrimitiveType
                )
                setWifiApEnabled.invoke(wifiManager, null, true)
                Log.d(TAG, "Restored hotspot (was enabled)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore hotspot: ${e.message}")
            }
        }

        Log.d(TAG, "App closed - all states restored")
    }

    private fun checkPermissions() {
        val needed = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        }

        // WRITE_SETTINGS requires special handling
        if (!Settings.System.canWrite(this)) {
            Log.d(TAG, "Requesting WRITE_SETTINGS permission")
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = android.net.Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private var settingsOpened = false

    private fun tryBind() {
        val ssid = getCurrentSsid()

        if (ssid != null) {
            settingsOpened = false

            // Check if we're on an ESP32 network
            if (isEsp32Network(ssid)) {
                Log.d(TAG, "Connected to ESP32 network: $ssid")
                bindToWifi(ssid)
            } else {
                // Not on ESP32, try to switch to it
                Log.d(TAG, "Currently on $ssid, checking for ESP32 networks...")
                tryConnectToEsp32()
            }
        } else {
            if (!wifiManager.isWifiEnabled) {
                // WiFi still off after forceWifiOn attempt
                showBlockedByHotspot()
            } else {
                // WiFi enabled but not connected
                tryConnectToEsp32OrOpenSettings()
            }
        }
    }

    private fun isEsp32Network(ssid: String): Boolean {
        // Check if SSID indicates ESP32 network
        return ssid.startsWith("ESP32", ignoreCase = true) ||
               ssid.contains("ESP32", ignoreCase = true) ||
               checkIfEsp32Subnet()
    }

    private fun checkIfEsp32Subnet(): Boolean {
        // Check if current IP is in 192.168.4.x range (ESP32 default)
        try {
            val info = wifiManager.connectionInfo
            val ipAddress = info?.ipAddress ?: return false

            // Convert int to IP address
            val ip = String.format(
                "%d.%d.%d.%d",
                ipAddress and 0xff,
                ipAddress shr 8 and 0xff,
                ipAddress shr 16 and 0xff,
                ipAddress shr 24 and 0xff
            )

            Log.d(TAG, "Current IP: $ip")
            return ip.startsWith("192.168.4.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check IP subnet", e)
            return false
        }
    }

    private fun tryConnectToEsp32() {
        updateStatus("Searching for ESP32...", true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: Use WifiNetworkSpecifier to request ESP32 network
            connectToEsp32Api29Plus()
        } else {
            // Android 8-9: Try to connect to saved ESP32 network
            connectToEsp32Legacy()
        }
    }

    private fun tryConnectToEsp32OrOpenSettings() {
        // WiFi enabled but not connected - try ESP32 or open settings
        updateStatus("Connecting to ESP32...", true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectToEsp32Api29Plus()
        } else {
            connectToEsp32Legacy()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectToEsp32Api29Plus() {
        try {
            // Check if custom credentials are configured
            val customCreds = credentials.getCredentials()

            val specifier = if (customCreds != null) {
                // Use custom SSID and password
                val (ssid, password) = customCreds
                Log.d(TAG, "Using custom credentials for: $ssid")
                WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                    .setWpa2Passphrase(password)
                    .build()
            } else {
                // Auto-detect any ESP32* network
                Log.d(TAG, "Auto-detecting ESP32 networks")
                WifiNetworkSpecifier.Builder()
                    .setSsidPattern(android.os.PatternMatcher("ESP32", android.os.PatternMatcher.PATTERN_PREFIX))
                    .build()
            }

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    connectivityManager.bindProcessToNetwork(network)
                    val ssid = getCurrentSsid() ?: "ESP32"
                    Log.d(TAG, "Connected to ESP32: $ssid")

                    runOnUiThread {
                        loadEsp32Interface()
                    }
                }

                override fun onUnavailable() {
                    Log.d(TAG, "ESP32 network unavailable")
                    runOnUiThread {
                        updateStatus("ESP32 not found\n\nLong-press to configure\nTap to open Settings", true)
                        statusText.setOnClickListener {
                            settingsOpened = true
                            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                        }
                    }
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "Lost ESP32 connection")
                    runOnUiThread {
                        showConnectionLostDialog()
                    }
                }
            }

            networkCallback = callback
            connectivityManager.requestNetwork(request, callback)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to request ESP32 network", e)
            updateStatus("Error connecting\n\nTap to open Settings", true)
            statusText.setOnClickListener {
                settingsOpened = true
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun connectToEsp32Legacy() {
        // Android 8-9: Try to find and connect to saved ESP32 network
        try {
            val configuredNetworks = wifiManager.configuredNetworks
            if (configuredNetworks == null) {
                Log.d(TAG, "No configured networks found")
                openWifiSettingsForEsp32()
                return
            }

            // Check if custom credentials are configured
            val customCreds = credentials.getCredentials()

            // Find ESP32 networks in saved configurations
            val esp32Networks = if (customCreds != null) {
                // Look for specific custom SSID
                val (targetSsid, _) = customCreds
                Log.d(TAG, "Looking for custom network: $targetSsid")
                configuredNetworks.filter { config ->
                    val ssid = config.SSID?.trim('"') ?: ""
                    ssid.equals(targetSsid, ignoreCase = true)
                }
            } else {
                // Auto-detect any ESP32* network
                Log.d(TAG, "Auto-detecting ESP32 networks")
                configuredNetworks.filter { config ->
                    val ssid = config.SSID?.trim('"') ?: ""
                    ssid.contains("ESP32", ignoreCase = true)
                }
            }

            if (esp32Networks.isEmpty()) {
                Log.d(TAG, "No saved ESP32 networks found")
                updateStatus("ESP32 not saved\n\nLong-press to configure\nTap to add network", true)
                statusText.setOnClickListener {
                    openWifiSettingsForEsp32()
                }
                return
            }

            // Try to connect to the first ESP32 network
            val esp32Config = esp32Networks.first()
            val esp32Ssid = esp32Config.SSID?.trim('"') ?: "ESP32"
            Log.d(TAG, "Found saved ESP32 network: $esp32Ssid (networkId: ${esp32Config.networkId})")

            updateStatus("Connecting to $esp32Ssid...", true)

            // Disable all networks and enable ESP32 network
            wifiManager.disconnect()

            val success = wifiManager.enableNetwork(esp32Config.networkId, true)
            if (success) {
                Log.d(TAG, "enableNetwork returned true for $esp32Ssid")
                wifiManager.reconnect()

                // Wait for connection and then bind
                statusText.postDelayed({
                    val currentSsid = getCurrentSsid()
                    if (currentSsid != null && isEsp32Network(currentSsid)) {
                        Log.d(TAG, "Successfully connected to ESP32: $currentSsid")
                        bindToWifi(currentSsid)
                    } else {
                        Log.d(TAG, "Connection failed or took too long, current SSID: $currentSsid")
                        updateStatus("Connection failed\n\nTap to open Settings", true)
                        statusText.setOnClickListener {
                            openWifiSettingsForEsp32()
                        }
                    }
                }, 3000)
            } else {
                Log.d(TAG, "enableNetwork returned false for $esp32Ssid")
                updateStatus("Cannot connect\n\nTap to open Settings", true)
                statusText.setOnClickListener {
                    openWifiSettingsForEsp32()
                }
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for legacy WiFi connection", e)
            openWifiSettingsForEsp32()
        } catch (e: Exception) {
            Log.e(TAG, "Legacy ESP32 connection failed", e)
            updateStatus("Error: ${e.message}\n\nTap to open Settings", true)
            statusText.setOnClickListener {
                openWifiSettingsForEsp32()
            }
        }
    }

    private fun openWifiSettingsForEsp32() {
        updateStatus("Please connect to ESP32 network\n\nLong-press to configure credentials", true)
        if (!settingsOpened) {
            settingsOpened = true
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    }

    private var accessibilityPrompted = false

    private fun showBlockedByHotspot() {
        val apState = getWifiApState()
        if (apState == 13 || apState == 12) { // AP_STATE_ENABLED or AP_STATE_ENABLING
            // Try to kill Bluetooth to prevent CarPlay interference
            killBluetooth()

            // Check if accessibility service is enabled
            if (CarPlayKillerService.isServiceEnabled(this)) {
                // Auto-trigger WiFi enable silently
                CarPlayKillerService.triggerEnableWifi()
            } else {
                // Need accessibility - prompt once
                if (!accessibilityPrompted) {
                    accessibilityPrompted = true
                    updateStatus("Setup required\n\nEnable accessibility service", true)
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                } else {
                    updateStatus("Enable accessibility service\n\nTap to open settings", true)
                    statusText.setOnClickListener {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        startActivity(intent)
                    }
                }
            }
        } else {
            updateStatus("Cannot enable WiFi\n\nTap to retry", true)
            statusText.setOnClickListener {
                forceWifiOn()
            }
        }
    }

    private fun getWifiApState(): Int {
        return try {
            val method = wifiManager.javaClass.getMethod("getWifiApState")
            method.invoke(wifiManager) as Int
        } catch (e: Exception) {
            Log.d(TAG, "getWifiApState failed: ${e.message}")
            0
        }
    }

    @Suppress("DEPRECATION")
    private fun getCurrentSsid(): String? {
        val info = wifiManager.connectionInfo ?: return null
        val ssid = info.ssid ?: return null
        if (ssid == "<unknown ssid>") return null
        return ssid.trim('"')
    }

    private fun bindToWifi(ssid: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bindToWifiApi29Plus(ssid)
        } else {
            bindToWifiLegacy(ssid)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun bindToWifiApi29Plus(ssid: String) {
        // Already connected to ESP32, just bind and load interface
        try {
            for (network in connectivityManager.allNetworks) {
                val caps = connectivityManager.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    connectivityManager.bindProcessToNetwork(network)
                    Log.d(TAG, "Bound to $ssid (API 29+)")
                    loadEsp32Interface()
                    return
                }
            }
            updateStatus("WiFi not available", true)
        } catch (e: Exception) {
            Log.e(TAG, "API 29+ bind failed", e)
            updateStatus("Error: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun bindToWifiLegacy(ssid: String) {
        try {
            for (network in connectivityManager.allNetworks) {
                val caps = connectivityManager.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    connectivityManager.bindProcessToNetwork(network)
                    Log.d(TAG, "Bound to $ssid (legacy)")
                    loadEsp32Interface()
                    return
                }
            }
            updateStatus("WiFi not available", true)
        } catch (e: Exception) {
            Log.e(TAG, "Legacy bind failed", e)
            updateStatus("Error: ${e.message}", true)
        }
    }

    private fun loadEsp32Interface() {
        runOnUiThread {
            webView.visibility = View.VISIBLE
            statusText.visibility = View.GONE
            webView.loadUrl(ESP32_URL)
            Log.d(TAG, "Loading ESP32 interface: $ESP32_URL")
        }
    }

    private fun showCredentialsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_credentials, null)
        val ssidInput = dialogView.findViewById<android.widget.EditText>(R.id.ssidInput)
        val passwordInput = dialogView.findViewById<android.widget.EditText>(R.id.passwordInput)

        // Pre-fill with existing credentials
        credentials.getCredentials()?.let { (ssid, password) ->
            ssidInput.setText(ssid)
            passwordInput.setText(password)
        }

        AlertDialog.Builder(this)
            .setTitle("ESP32 Network Configuration")
            .setMessage("Enter ESP32 WiFi credentials\n(Leave empty to auto-detect any ESP32* network)")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                val ssid = ssidInput.text.toString().trim()
                val password = passwordInput.text.toString()

                if (ssid.isNotEmpty() && password.isNotEmpty()) {
                    credentials.saveCredentials(ssid, password)
                    updateStatus("Credentials saved\nReconnecting...", true)
                    statusText.postDelayed({ forceWifiOn() }, 1000)
                } else if (ssid.isEmpty() && password.isEmpty()) {
                    credentials.clearCredentials()
                    updateStatus("Auto-detect mode\nReconnecting...", true)
                    statusText.postDelayed({ forceWifiOn() }, 1000)
                } else {
                    updateStatus("Error: Both SSID and password required", true)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("Clear") { dialog, _ ->
                credentials.clearCredentials()
                updateStatus("Credentials cleared", true)
                dialog.dismiss()
            }
            .show()
    }

    private fun showConnectionLostDialog() {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Connection Lost")
                .setMessage("WiFi connection to ESP32 was lost")
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    finish()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun updateStatus(msg: String, show: Boolean = true) {
        Log.d(TAG, msg)
        runOnUiThread {
            statusText.text = msg
            statusText.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) tryBind()
    }
}
