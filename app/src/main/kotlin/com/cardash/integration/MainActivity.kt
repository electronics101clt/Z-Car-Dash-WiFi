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
    private lateinit var webView: WebView
    private lateinit var dashboardContainer: android.view.ViewGroup
    private lateinit var statusIcon: TextView
    private lateinit var networkName: TextView
    private lateinit var statusMessage: TextView
    private lateinit var ipAddress: TextView
    private lateinit var refreshButton: android.widget.Button
    private lateinit var wifiSettingsButton: android.widget.Button
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private lateinit var credentials: NetworkCredentials

    private val TAG = "WifiSelector"
    private val ZS_URL = "http://192.168.4.1"

    // Fallback passwords to try for ZS- devices
    private val ZS_FALLBACK_PASSWORDS = listOf(
        "A5k6Wm",
        "12345678",
        "password",
        "admin123"
    )

    private var bluetoothWasEnabled = false
    private var hotspotWasEnabled = false
    private var refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var hasRoot = false
    private var currentPasswordAttempt = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize crash handler (Aptoide pattern)
        CrashHandler.init(this)

        try {
            setContentView(R.layout.activity_main)

            wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            webView = findViewById(R.id.webView)
            dashboardContainer = findViewById(R.id.dashboardContainer)
            statusIcon = findViewById(R.id.statusIcon)
            networkName = findViewById(R.id.networkName)
            statusMessage = findViewById(R.id.statusMessage)
            ipAddress = findViewById(R.id.ipAddress)
            refreshButton = findViewById(R.id.refreshButton)
            wifiSettingsButton = findViewById(R.id.wifiSettingsButton)
            credentials = NetworkCredentials(this)

            setupWebView()
            setupDashboard()
            detectRoot()
            checkPermissions()
        } catch (e: Exception) {
            CrashHandler.log("onCreate failed", e)
            // Show error UI instead of crashing
            showDashboard("Initialization Error", "❌", "App failed to initialize: ${e.message}")
        }
    }

    private fun detectRoot() {
        // Silently check for root (common on Chinese head units with debug builds)
        hasRoot = RootHelper.canUseRoot()
        if (hasRoot) {
            Log.d(TAG, "Root access detected - using privileged mode")
            showDashboard("Root Detected", "🔓", "Privileged mode enabled\nChinese head unit debug build detected", "")
            refreshHandler.postDelayed({
                dashboardContainer.visibility = View.GONE
            }, 1500)
        } else {
            Log.d(TAG, "No root access - using userland mode")
        }
    }

    private fun setupDashboard() {
        refreshButton.setOnClickListener {
            showDashboard("Refreshing...", "🔄", "Checking WiFi connection...")
            forceWifiOn()
        }

        wifiSettingsButton.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
        }

        // Long press on network name to configure custom credentials
        networkName.setOnLongClickListener {
            showCredentialsDialog()
            true
        }
    }

    private fun setupWebView() {
        try {
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    try {
                        super.onPageStarted(view, url, favicon)
                        // Dashboard already hidden when webview loads
                    } catch (e: Exception) {
                        CrashHandler.log("WebView onPageStarted error", e)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    try {
                        super.onPageFinished(view, url)
                        // Page loaded successfully
                    } catch (e: Exception) {
                        CrashHandler.log("WebView onPageFinished error", e)
                    }
                }

                override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    try {
                        super.onReceivedError(view, request, error)
                        showDashboard("Connection Error", "❌", "Failed to load ZS interface")
                    } catch (e: Exception) {
                        CrashHandler.log("WebView onReceivedError handler error", e)
                    }
                }
            }

            // Start hidden
            webView.visibility = View.GONE
        } catch (e: Exception) {
            CrashHandler.log("setupWebView failed", e)
            showDashboard("WebView Error", "❌", "Failed to initialize web interface")
        }
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

            // Try root method first (works on all Android versions, no user prompt)
            if (hasRoot) {
                Log.d(TAG, "Attempting to enable WiFi with root")
                if (RootHelper.forceEnableWifi()) {
                    Log.d(TAG, "WiFi enabled with root, waiting...")
                    refreshHandler.postDelayed({ tryBind() }, 1500)
                    return
                } else {
                    Log.w(TAG, "Root WiFi enable failed, falling back to API")
                }
            }

            // Fallback: Try setWifiEnabled (works on Android 9 and below)
            val success = try {
                wifiManager.setWifiEnabled(true)
            } catch (e: Exception) {
                Log.e(TAG, "setWifiEnabled exception: ${e.message}")
                false
            }

            if (success) {
                Log.d(TAG, "setWifiEnabled returned true, waiting...")
                refreshHandler.postDelayed({ tryBind() }, 1500)
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

            if (!hotspotWasEnabled) {
                Log.d(TAG, "killSoftAp: Hotspot already off")
                return
            }

            // Try root method first (silent, works on Chinese head units)
            if (hasRoot) {
                Log.d(TAG, "killSoftAp: Attempting with root")
                if (RootHelper.disableHotspot()) {
                    Log.d(TAG, "killSoftAp: Successfully disabled with root")
                    return
                } else {
                    Log.w(TAG, "killSoftAp: Root method failed, falling back to reflection")
                }
            }

            // Fallback to reflection method
            val setWifiApEnabled = wifiManager.javaClass.getMethod(
                "setWifiApEnabled",
                android.net.wifi.WifiConfiguration::class.java,
                Boolean::class.javaPrimitiveType
            )
            setWifiApEnabled.invoke(wifiManager, null, false)
            Log.d(TAG, "killSoftAp: setWifiApEnabled(null, false) called (was enabled: $hotspotWasEnabled)")
        } catch (e: Exception) {
            CrashHandler.log("killSoftAp failed", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun killBluetooth() {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter != null) {
                // Save Bluetooth state before killing
                bluetoothWasEnabled = bluetoothAdapter.isEnabled

                if (!bluetoothAdapter.isEnabled) {
                    Log.d(TAG, "killBluetooth: Bluetooth already off")
                    return
                }

                // Try root method first (silent, guaranteed to work on debug builds)
                if (hasRoot) {
                    Log.d(TAG, "killBluetooth: Attempting with root")
                    if (RootHelper.disableBluetooth()) {
                        Log.d(TAG, "killBluetooth: Successfully disabled with root")
                        return
                    } else {
                        Log.w(TAG, "killBluetooth: Root method failed, falling back to API")
                    }
                }

                // Fallback to normal API
                val success = bluetoothAdapter.disable()
                Log.d(TAG, "killBluetooth: disable() returned $success (was enabled: $bluetoothWasEnabled)")
                if (!success) {
                    Log.d(TAG, "killBluetooth: automatic disable failed")
                }
            }
        } catch (e: Exception) {
            CrashHandler.log("killBluetooth failed", e)
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

            // Check if we're on an ZS network
            if (isEsp32Network(ssid)) {
                Log.d(TAG, "Connected to ZS network: $ssid")
                val ip = getCurrentIpAddress()
                showDashboard(ssid, "✅", "Connected to ZS network", ip)
                bindToWifi(ssid)
            } else {
                // Not on ZS, try to switch to it
                Log.d(TAG, "Currently on $ssid, checking for ZS networks...")
                showDashboard(ssid, "⚠️", "Not an ZS network - switching...", getCurrentIpAddress())
                tryConnectToEsp32()
            }
        } else {
            if (!wifiManager.isWifiEnabled) {
                // WiFi still off after forceWifiOn attempt
                showBlockedByHotspot()
            } else {
                // WiFi enabled but not connected
                showDashboard("No WiFi", "❌", "WiFi enabled but not connected")
                tryConnectToEsp32OrOpenSettings()
            }
        }
    }

    private fun isEsp32Network(ssid: String): Boolean {
        // Check if SSID indicates ZS network
        return ssid.startsWith("ZS", ignoreCase = true) ||
               ssid.contains("ZS", ignoreCase = true) ||
               checkIfEsp32Subnet()
    }

    private fun checkIfEsp32Subnet(): Boolean {
        // Check if current IP is in 192.168.4.x range (ZS default)
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
        showDashboard("Searching...", "🔍", "Looking for ZS network...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: Use WifiNetworkSpecifier to request ZS network
            connectToEsp32Api29Plus()
        } else {
            // Android 8-9: Try to connect to saved ZS network
            connectToEsp32Legacy()
        }
    }

    private fun tryConnectToEsp32OrOpenSettings() {
        // WiFi enabled but not connected - try ZS or open settings
        showDashboard("Connecting...", "🔄", "Connecting to ZS...")
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
                // Auto-detect any ZS* network
                Log.d(TAG, "Auto-detecting ZS networks")
                WifiNetworkSpecifier.Builder()
                    .setSsidPattern(android.os.PatternMatcher("ZS", android.os.PatternMatcher.PATTERN_PREFIX))
                    .build()
            }

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    try {
                        connectivityManager.bindProcessToNetwork(network)
                        val ssid = getCurrentSsid() ?: "ZS"
                        Log.d(TAG, "Connected to ZS: $ssid")

                        runOnUiThread {
                            loadEsp32Interface()
                        }
                    } catch (e: Exception) {
                        CrashHandler.log("Network callback onAvailable error", e)
                        runOnUiThread {
                            showDashboard("Connection Error", "❌", "Failed to bind to network: ${e.message}")
                        }
                    }
                }

                override fun onUnavailable() {
                    try {
                        Log.d(TAG, "ZS network unavailable")

                        // If we have root and custom credentials aren't set, try fallback passwords
                        if (hasRoot && customCreds == null) {
                            Log.d(TAG, "Trying root-based password fallback for ZS- networks")
                            runOnUiThread {
                                showDashboard("Scanning...", "🔍", "Scanning for ZS- networks with fallback passwords...")
                            }

                            // Try to scan for ZS- networks and connect with fallback passwords
                            Thread {
                                try {
                                    val zsNetwork = RootHelper.scanForZsNetworks()
                                    if (zsNetwork != null) {
                                        runOnUiThread {
                                            showDashboard("Trying Passwords", "🔑", "Found $zsNetwork\nAttempting automatic connection...")
                                        }

                                        if (RootHelper.tryZsPasswords(zsNetwork, ZS_FALLBACK_PASSWORDS)) {
                                            runOnUiThread {
                                                showDashboard("Connected!", "✅", "Successfully connected using fallback password")
                                                refreshHandler.postDelayed({ tryBind() }, 2000)
                                            }
                                            return@Thread
                                        }
                                    }
                                } catch (e: Exception) {
                                    CrashHandler.log("Root password fallback failed", e)
                                }

                                // All attempts failed
                                runOnUiThread {
                                    showDashboard("ZS Not Found", "❌", "ZS network not available or passwords don't match.\nCheck if ZS is powered on and broadcasting WiFi.\n\nLong-press network name to configure custom SSID.")
                                }
                            }.start()
                        } else {
                            runOnUiThread {
                                showDashboard("ZS Not Found", "❌", "ZS network not available.\nCheck if ZS is powered on and broadcasting WiFi.\n\nLong-press network name to configure custom SSID.")
                            }
                        }
                    } catch (e: Exception) {
                        CrashHandler.log("Network callback onUnavailable error", e)
                    }
                }

                override fun onLost(network: Network) {
                    try {
                        Log.d(TAG, "Lost ZS connection")
                        runOnUiThread {
                            showConnectionLostDialog()
                        }
                    } catch (e: Exception) {
                        CrashHandler.log("Network callback onLost error", e)
                    }
                }
            }

            networkCallback = callback
            connectivityManager.requestNetwork(request, callback)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to request ZS network", e)
            showDashboard("Connection Error", "❌", "Failed to connect to ZS.\nUse WiFi Settings button to configure network.")
        }
    }

    @Suppress("DEPRECATION")
    private fun connectToEsp32Legacy() {
        // Android 8-9: Try to find and connect to saved ZS network
        try {
            val configuredNetworks = wifiManager.configuredNetworks
            if (configuredNetworks == null) {
                Log.d(TAG, "No configured networks found")
                openWifiSettingsForEsp32()
                return
            }

            // Check if custom credentials are configured
            val customCreds = credentials.getCredentials()

            // Find ZS networks in saved configurations
            val esp32Networks = if (customCreds != null) {
                // Look for specific custom SSID
                val (targetSsid, _) = customCreds
                Log.d(TAG, "Looking for custom network: $targetSsid")
                configuredNetworks.filter { config ->
                    val ssid = config.SSID?.trim('"') ?: ""
                    ssid.equals(targetSsid, ignoreCase = true)
                }
            } else {
                // Auto-detect any ZS* network
                Log.d(TAG, "Auto-detecting ZS networks")
                configuredNetworks.filter { config ->
                    val ssid = config.SSID?.trim('"') ?: ""
                    ssid.contains("ZS", ignoreCase = true)
                }
            }

            if (esp32Networks.isEmpty()) {
                Log.d(TAG, "No saved ZS networks found")
                showDashboard("ZS Not Saved", "⚠️", "No saved ZS network found.\nUse WiFi Settings button to add ZS network.\n\nLong-press network name to configure custom SSID.")
                return
            }

            // Try to connect to the first ZS network
            val esp32Config = esp32Networks.first()
            val esp32Ssid = esp32Config.SSID?.trim('"') ?: "ZS"
            Log.d(TAG, "Found saved ZS network: $esp32Ssid (networkId: ${esp32Config.networkId})")

            updateStatus("Connecting to $esp32Ssid...", true)

            // Disable all networks and enable ZS network
            wifiManager.disconnect()

            val success = wifiManager.enableNetwork(esp32Config.networkId, true)
            if (success) {
                Log.d(TAG, "enableNetwork returned true for $esp32Ssid")
                wifiManager.reconnect()

                // Wait for connection and then bind
                refreshHandler.postDelayed({
                    val currentSsid = getCurrentSsid()
                    if (currentSsid != null && isEsp32Network(currentSsid)) {
                        Log.d(TAG, "Successfully connected to ZS: $currentSsid")
                        bindToWifi(currentSsid)
                    } else {
                        Log.d(TAG, "Connection failed or took too long, current SSID: $currentSsid")
                        showDashboard("Connection Failed", "❌", "Could not connect to ZS.\nUse WiFi Settings button to manually connect.")
                    }
                }, 3000)
            } else {
                Log.d(TAG, "enableNetwork returned false for $esp32Ssid")
                showDashboard("Cannot Connect", "❌", "Failed to enable ZS network.\nUse WiFi Settings button to manually connect.")
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for legacy WiFi connection", e)
            showDashboard("Permission Denied", "❌", "WiFi permission denied.\nGrant permissions and use WiFi Settings button.")
            openWifiSettingsForEsp32()
        } catch (e: Exception) {
            Log.e(TAG, "Legacy ZS connection failed", e)
            showDashboard("Error", "❌", "Connection error: ${e.message}\nUse WiFi Settings button to configure.")
        }
    }

    private fun openWifiSettingsForEsp32() {
        showDashboard("Manual Setup", "⚙️", "Please connect to ZS network manually.\n\nLong-press network name to configure custom credentials.")
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
                    showDashboard("Setup Required", "⚙️", "Enable accessibility service to auto-disable hotspot")
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                } else {
                    showDashboard("Accessibility Needed", "⚙️", "Enable accessibility service to continue.\nUse WiFi Settings button to configure.")
                }
            }
        } else {
            showDashboard("WiFi Disabled", "❌", "Cannot enable WiFi.\nUse Refresh button to retry.")
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

    @Suppress("DEPRECATION")
    private fun getCurrentIpAddress(): String {
        try {
            val info = wifiManager.connectionInfo
            val ipAddress = info?.ipAddress ?: return ""
            return String.format(
                "%d.%d.%d.%d",
                ipAddress and 0xff,
                ipAddress shr 8 and 0xff,
                ipAddress shr 16 and 0xff,
                ipAddress shr 24 and 0xff
            )
        } catch (e: Exception) {
            return ""
        }
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
        // Already connected to ZS, just bind and load interface
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
            hideDashboard()
            webView.loadUrl(ZS_URL)
            Log.d(TAG, "Loading ZS interface: $ZS_URL")
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
            .setTitle("ZS Network Configuration")
            .setMessage("Enter ZS WiFi credentials\n(Leave empty to auto-detect any ZS* network)")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                val ssid = ssidInput.text.toString().trim()
                val password = passwordInput.text.toString()

                if (ssid.isNotEmpty() && password.isNotEmpty()) {
                    credentials.saveCredentials(ssid, password)
                    showDashboard("Credentials Saved", "✅", "Reconnecting to $ssid...")
                    refreshHandler.postDelayed({ forceWifiOn() }, 1000)
                } else if (ssid.isEmpty() && password.isEmpty()) {
                    credentials.clearCredentials()
                    showDashboard("Auto-Detect Mode", "🔄", "Searching for ZS networks...")
                    refreshHandler.postDelayed({ forceWifiOn() }, 1000)
                } else {
                    showDashboard("Error", "❌", "Both SSID and password required")
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
                .setMessage("WiFi connection to ZS was lost")
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    finish()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun showDashboard(network: String = "Searching for ZS...", icon: String = "📡", message: String = "Checking WiFi connection...", ip: String = "") {
        Log.d(TAG, "Dashboard: $network - $message")
        runOnUiThread {
            networkName.text = network
            statusIcon.text = icon
            statusMessage.text = message
            if (ip.isNotEmpty()) {
                ipAddress.text = "IP: $ip"
                ipAddress.visibility = View.VISIBLE
            } else {
                ipAddress.visibility = View.GONE
            }
            dashboardContainer.visibility = View.VISIBLE
            webView.visibility = View.GONE
        }
    }

    private fun hideDashboard() {
        runOnUiThread {
            dashboardContainer.visibility = View.GONE
            webView.visibility = View.VISIBLE
        }
    }

    private fun updateStatus(msg: String, show: Boolean = true) {
        Log.d(TAG, msg)
        if (show) {
            showDashboard(message = msg)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) tryBind()
    }
}
