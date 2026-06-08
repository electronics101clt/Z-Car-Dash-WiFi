<!--
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │                        © 2024 ZScreen Electronics                           │
  │                          All Rights Reserved.                               │
  │                                                                             │
  │  This software is protected by U.S. copyright law (17 U.S.C. § 102).        │
  │  The copyright holder retains exclusive rights to reproduction,             │
  │  distribution, and derivative works.                                        │
  │                                                                             │
  │  While this repository is publicly available for viewing and learning,      │
  │  unauthorized reproduction or distribution without explicit permission      │
  │  from ZScreen Electronics violates federal law and may result in civil      │
  │  and criminal penalties.                                                    │
  │                                                                             │
  │  For licensing inquiries: https://zscreenusa.com                            │
  └─────────────────────────────────────────────────────────────────────────────┘
-->

<p align="center">
  <strong>© 2024 ZScreen Electronics. All Rights Reserved.</strong><br>
  <a href="https://zscreenusa.com">zscreenusa.com</a>
</p>

<p align="center">
  <em>This software is protected by U.S. copyright law (17 U.S.C. § 102). The copyright holder retains exclusive rights to reproduction, distribution, and derivative works. Public availability does not grant usage rights without explicit license. For licensing inquiries, visit <a href="https://zscreenusa.com">zscreenusa.com</a>.</em>
</p>

---

# Z Car Dash WiFi

Android app that automatically connects to ZS WiFi networks and displays their web interface, bypassing hotspot mode and CarPlay interference using silent root detection inspired by Aptoide's approach.

## Features

- **Automatic WiFi Connection**: Forces WiFi on and connects to ZS access points
- **Hotspot Override**: Kills device hotspot to free WiFi hardware
- **CarPlay Bypass**: Automatically disables Bluetooth to prevent CarPlay interference
- **State Restoration**: Restores Bluetooth and hotspot when app closes
- **WebView Interface**: Displays ZS web interface directly in app
- **Multi-Android Support**: Works on Android 8.0, 9.0, and 10.0+
- **Silent Root Detection**: Detects and exploits root access on Chinese head units with debug builds (Aptoide-inspired)
- **Crash Avoidance**: Global exception handler prevents crashes, logs errors instead (based on Aptoide patterns)
- **Privileged Operations**: Bypasses Android 10+ WiFi restrictions using `svc` commands when root available

## Aptoide Behavior Research & Exploitation

**Why Aptoide?** We noticed that the Aptoide app store silently detects root access on Chinese car head units running debug builds and automatically uses privileged operations without user prompts. This behavior is perfect for car dashboards where user interaction should be minimal.

### Research Methodology

We analyzed Aptoide's open-source client ([aptoide-client-v8](https://github.com/Aptoide/aptoide-client-v8)) and discovered their approach:

1. **Silent Root Detection**: Check for `su` binary in common locations without triggering authorization prompts
2. **Debug Build Exploitation**: Chinese head units often ship with debug builds that have `su` available without SuperSU/Magisk
3. **Graceful Fallback**: If root fails, fall back to standard Android APIs
4. **No User Prompts**: Everything happens automatically - no permission dialogs

### Our Implementation

#### Root Detection (`RootHelper.kt`)

Based on Aptoide's `CrashReport` and root detection patterns:

```kotlin
fun isRootAvailable(): Boolean {
    val suPaths = arrayOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su",
        "/system/su", "/su/bin/su", "/data/local/xbin/su"
    )
    return suPaths.any { File(it).exists() }
}

private fun canExecuteRoot(): Boolean {
    // Silent test - no prompts on debug builds
    val result = executeCommandSilent("echo root_test")
    return result?.contains("root_test") == true
}

fun canUseRoot(): Boolean {
    return isRootAvailable() && canExecuteRoot()
}
```

**Key Insight**: The `canExecuteRoot()` function doesn't use `id` or other commands that might trigger prompts. It just echoes a test string silently.

#### Privileged Operations

When root is detected, we bypass Android 10+ WiFi restrictions:

```kotlin
fun forceEnableWifi(): Boolean {
    // Works on ALL Android versions, no user prompt
    val result = executeCommand("svc wifi enable")
    return result != null
}

fun disableHotspot(): Boolean {
    // Try multiple methods for different Android versions
    val methods = arrayOf(
        "svc wifi disable-ap",
        "svc wifi disable-softap",
        "cmd wifi stop-softap"
    )
    for (method in methods) {
        if (executeCommand(method) != null) return true
    }
    return false
}
```

**Benefit**: On Android 10+, normal apps can't programmatically enable WiFi. With root, we can use `svc wifi enable` to bypass this completely.

### Crash Avoidance Implementation

Based on Aptoide's crash handling patterns found in their source:

#### Global Exception Handler (`CrashHandler.kt`)

Aptoide uses `Thread.setDefaultUncaughtExceptionHandler()` to catch all crashes globally. We implemented the same:

```kotlin
class CrashHandler private constructor(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        fun init(context: Context) {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            instance = CrashHandler(context, defaultHandler)
            Thread.setDefaultUncaughtExceptionHandler(instance)
        }

        fun log(e: Throwable) {
            Log.e(TAG, "Exception logged: ${e.message}", e)
            instance?.logToFile(e)
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            Log.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)
            logToFile(throwable, "UNCAUGHT in ${thread.name}")
            handleCrash(throwable)
        } catch (e: Exception) {
            Log.e(TAG, "Error in crash handler itself", e)
        } finally {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
```

#### Try-Catch Everywhere

Following Aptoide's pattern of wrapping all potentially unsafe operations:

```kotlin
// Network callbacks
override fun onAvailable(network: Network) {
    try {
        connectivityManager.bindProcessToNetwork(network)
        loadEsp32Interface()
    } catch (e: Exception) {
        CrashHandler.log("Network callback error", e)
        showDashboard("Connection Error", "❌", "Failed to bind")
    }
}

// Root operations
try {
    if (hasRoot) {
        RootHelper.forceEnableWifi()
    }
} catch (e: Exception) {
    CrashHandler.log("Root WiFi enable failed", e)
}
```

**Research Sources:**
- [Aptoide GitHub](https://github.com/aptoide) - Official Aptoide repositories
- [aptoide-client-v8](https://github.com/Aptoide/aptoide-client-v8) - Main Android client source code
- [DeepLinkIntentReceiver.java](https://github.com/Aptoide/aptoide-client-v8/blob/master/app/src/main/java/cm/aptoide/pt/DeepLinkIntentReceiver.java) - Exception handling examples
- [Global Exception Handling in Android](https://medium.com/@boyrazgiray/how-to-catch-handle-exceptions-globally-in-android-d3447064df14) - Best practices
- [Android Exception Handling](https://blog.sentry.io/how-to-handle-android-exceptions-and-avoid-application-crashes/) - Sentry's guide

## How It Works

### Root Detection & Privileged Mode (All Android Versions)

**On startup**, the app silently checks for root access:

1. Searches for `su` binary in common paths (`/system/bin/su`, `/system/xbin/su`, etc.)
2. Silently tests if root commands work (no authorization prompts on debug builds)
3. If root available, enables **privileged mode**:
   - Uses `svc wifi enable` to bypass Android 10+ WiFi restrictions
   - Uses `svc wifi disable-ap` to kill hotspot
   - Uses `svc bluetooth disable` to prevent CarPlay interference
   - No user interaction required - everything happens silently

**If no root**, falls back to userland methods:
- Android 10+: Uses WiFi Settings panel (requires user tap)
- Android 8-9: Uses deprecated `setWifiEnabled()` API (still works)

### Android 10+ (API 29+)

**With Root**: Uses `svc wifi enable` command - works perfectly, no user interaction

**Without Root**: Uses WiFi Network Request API with `removeCapability(NET_CAPABILITY_INTERNET)` to suppress "no internet" warnings and maintain stable connection to local ZS networks.

### Android 8-9 (API 26-28)

**With Root**: Uses `svc wifi enable` command for guaranteed success

**Without Root**: Uses legacy `bindProcessToNetwork()` and `setWifiEnabled()` methods. System notifications may appear but connection remains stable.

## App Behavior

### When App Opens:
1. **Initializes crash handler** (global exception handler, Aptoide pattern)
2. **Silently detects root access** (Aptoide-inspired, no prompts on debug builds)
3. Saves current Bluetooth and hotspot state
4. Disables Bluetooth (prevents CarPlay auto-connect) - **root if available, else API**
5. Kills WiFi hotspot (frees WiFi hardware) - **root if available, else reflection**
6. Forces WiFi client mode on - **root if available, else userland method**
7. Connects to ZS WiFi network
8. Displays ZS web interface at 192.168.4.1

### When App Closes:
1. Releases WiFi binding
2. Restores Bluetooth to original state
3. Restores hotspot to original state
4. Returns control to Android system

**No persistent interference** - app only affects WiFi/Bluetooth while running.

## ZS Configuration

Compatible with ZS access points using:
- **SSID**: `ZS-XXXXXXXX` (dynamic - 8-digit random serial number, e.g., `ZS-12345678`)
- **Password**: 12345678 (configurable)
- **IP**: 192.168.4.1
- **Protocol**: HTTP (cleartext)

**Note**: The ESP32 generates a random 8-digit serial number on first boot and creates an SSID like `ZS-12345678`. This number is persistent across reboots. The Android app must be configured to match your specific ESP32's SSID.

### Automatic OBD-II Integration

The ESP32 companion automatically scans for and connects to Bluetooth Low Energy (BLE) ELM327 adapters:
- Scans on boot for devices named "OBD", "ELM", "V-LINK", or "IOS-"
- Auto-reconnects every 15 seconds if connection is lost
- Queries real-time vehicle data (speed, RPM, temperature, etc.) every 200ms
- Falls back to simulation mode if no adapter found
- No user interaction required - fully automatic

See companion project: [esp32_car_dashboard](https://github.com/electronics101clt/esp32_car_dashboard)

## Permissions Required

- `INTERNET` - WebView access to ZS
- `BLUETOOTH` - Query Bluetooth state
- `BLUETOOTH_ADMIN` - Disable/enable Bluetooth
- `ACCESS_WIFI_STATE` - Check WiFi status
- `CHANGE_WIFI_STATE` - Force WiFi on/off
- `ACCESS_NETWORK_STATE` - Monitor network connection
- `CHANGE_NETWORK_STATE` - Bind to WiFi network
- `ACCESS_FINE_LOCATION` - Required for WiFi scanning (Android requirement)
- `ACCESS_COARSE_LOCATION` - Required for WiFi scanning
- `WRITE_SETTINGS` - Modify system settings

### Optional: Accessibility Service
For devices where hotspot blocks WiFi forcing, the app includes an accessibility service that can automatically tap the WiFi toggle in Settings. User must manually enable this in Android Settings → Accessibility.

## Installation

### From Source:
```bash
git clone https://github.com/electronics101clt/ZScreenDash.git
cd ZScreenDash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### From APK:
Download latest APK from [Releases](https://github.com/electronics101clt/ZScreenDash/releases) and install via ADB or file manager.

## Usage

1. Flash ZS with web server firmware
2. Install Z Car Dash on Android device
3. Open Z Car Dash app
4. App automatically connects to ZS
5. Web interface appears
6. Close app when done (restores original state)

## Project Structure

```
app/src/main/kotlin/com/cardash/integration/
├── MainActivity.kt          # Main activity with WiFi management
├── RootHelper.kt           # Silent root detection and privileged operations (Aptoide-inspired)
├── CrashHandler.kt         # Global exception handler (Aptoide pattern)
├── CarPlayKillerService.kt # Accessibility service for UI automation
└── NetworkCredentials.kt   # Encrypted credential storage
```

### Key Files

**RootHelper.kt** - Root detection and privileged operations
- Silently detects `su` binary
- Tests root execution without prompts
- Provides `forceEnableWifi()`, `disableHotspot()`, `disableBluetooth()` using `svc` commands
- Gracefully falls back if root unavailable

**CrashHandler.kt** - Global crash prevention
- Implements `Thread.UncaughtExceptionHandler`
- Catches ALL unhandled exceptions app-wide
- Logs crashes to files in `crash_logs/` directory
- Prevents app crashes, shows user-friendly error dialogs instead

**MainActivity.kt** - Core WiFi management logic
- Tries root methods first, falls back to userland
- Try-catch blocks around all critical operations
- Network callbacks with exception handling
- WebView with error recovery

## Technical Details

### Silent Root Detection (Aptoide Pattern)

**Research Finding**: Aptoide doesn't prompt for root authorization on Chinese debug builds. They silently test if `su` works and use it automatically.

```kotlin
fun canUseRoot(): Boolean {
    if (!isRootAvailable()) return false

    // Silent test - no prompts
    val result = executeCommandSilent("echo root_test")
    return result?.contains("root_test") == true
}

private fun executeCommandSilent(command: String): String? {
    try {
        val process = Runtime.getRuntime().exec("su")
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
    }
}
```

**Why this works on Chinese head units:**
- Debug builds include `su` binary at `/system/bin/su`
- No SuperSU or Magisk authorization framework
- Root commands execute immediately without prompts
- Perfect for car dashboards where user interaction is unwanted

### WiFi Network Request API (Android 10+)

```kotlin
val specifier = WifiNetworkSpecifier.Builder()
    .setSsid("ZS-Control")
    .build()

val request = NetworkRequest.Builder()
    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    .setNetworkSpecifier(specifier)
    .build()

connectivityManager.requestNetwork(request, callback)
```

The key line `.removeCapability(NET_CAPABILITY_INTERNET)` tells Android this is an intentional local-only network, preventing "no internet" warnings and auto-disconnection.

### Bluetooth Disabling

```kotlin
val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
bluetoothAdapter.disable()
```

Prevents CarPlay from auto-connecting via Bluetooth while app is active. Restored on app close.

### Hotspot Killing

```kotlin
val setWifiApEnabled = wifiManager.javaClass.getMethod(
    "setWifiApEnabled",
    android.net.wifi.WifiConfiguration::class.java,
    Boolean::class.javaPrimitiveType
)
setWifiApEnabled.invoke(wifiManager, null, false)
```

Uses reflection to access hidden Android API for disabling WiFi hotspot.

## Compatibility

- **Android 8.0 (API 26)** - Minimum supported version
- **Android 9.0 (API 28)** - Full support, may show "no internet" notification
- **Android 10.0+ (API 29+)** - Full support, no warnings
- **Android 12 (API 31)** - Bluetooth disable works
- **Android 13+ (API 33+)** - Bluetooth disable restricted for non-system apps

## Use Cases

- Car head units with CarPlay that block WiFi
- IoT device configuration and control
- ZS-based projects requiring persistent WiFi connection
- Local network access on devices that prefer cellular data

## Related Projects

- [esp32_car_dashboard](https://github.com/electronics101clt/esp32_car_dashboard) - ZS car dashboard with 8 gauges

## License

MIT License - See LICENSE file for details

## Credits

Developed for controlling ZS devices from Android head units with CarPlay interference.

Built with:
- Kotlin
- Android SDK
- WiFi Network Request API
- WebView
- Accessibility Services

---

**Note**: This app temporarily disables Bluetooth and hotspot while running. It restores them when closed. Not recommended for devices that require constant Bluetooth or hotspot operation.
