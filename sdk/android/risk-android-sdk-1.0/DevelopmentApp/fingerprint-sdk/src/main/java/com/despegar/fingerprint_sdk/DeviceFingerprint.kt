package com.despegar.fingerprintsdk

import android.Manifest
import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.res.Resources
import android.graphics.Point
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Address
import android.location.Geocoder
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import com.android.volley.BuildConfig
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

private const val TAG = "fingerprint-sdk"
const val RiskSDKSessionId = "RiskSDKSessionId"
const val RiskSDKSessionIdCreationTime = "RiskSDKSessionIdCreationTime"
const val RiskSDKPreferences = "RiskSDKPreferences"

object DeviceFingerprint {

    private var retryCount = 0
    private var minimumDelay = 5.0
    private val retryMax = 3
    public const val defaultURL = "https://api-sandbox.koin.com.br/fingerprint/session/mobile"
    private var currentURL = defaultURL
    private var currentOrganizationId: String = ""
    private lateinit var storedData: MutableMap<String, Any?>


    fun register(organizationId: String, url: String) {
        currentURL = url
        currentOrganizationId = organizationId
    }

    fun profileWithSessionId(c: Context, sessionId: String) {
        setSessionId(c, sessionId)
        GlobalScope.launch {
            gatherDeviceInformation(c)
        }
    }

    fun profile(c: Context): String {
        expireSessionIdIfNeeded(c)
        val sessionID = getSessionId(c)
        return if (sessionID != "") {
            GlobalScope.launch {
                gatherDeviceInformation(c)
            }
            sessionID
        } else {
            val newSessionId = createSessionId()
            setSessionId(c, newSessionId)
            GlobalScope.launch {
                gatherDeviceInformation(c)
            }
            newSessionId
        }
    }

    private fun createSessionId(): String {
        return UUID.randomUUID().toString()
    }

    private fun setSessionId(context: Context, uuidString: String) {
        val sharedPreferences =
            context.getSharedPreferences(RiskSDKPreferences, Context.MODE_PRIVATE) ?: return
        with(sharedPreferences.edit()) {
            putString(RiskSDKSessionId, uuidString)
            putLong(RiskSDKSessionIdCreationTime, System.currentTimeMillis())
            apply()
        }
    }

    private fun getSessionId(context: Context): String {
        val sharedPreferences =
            context.getSharedPreferences(RiskSDKPreferences, Context.MODE_PRIVATE)
        return sharedPreferences.getString(RiskSDKSessionId, "").toString()
    }

    private fun expireSessionIdIfNeeded(context: Context) {
        val sharedPreferences =
            context.getSharedPreferences(RiskSDKPreferences, Context.MODE_PRIVATE)
        val time = sharedPreferences.getLong(RiskSDKSessionIdCreationTime, -1)
        if ((System.currentTimeMillis() - time) >= 7200000) {
            with(sharedPreferences.edit()) {
                putString(RiskSDKSessionId, "")
                putLong(RiskSDKSessionIdCreationTime, -1)
                apply()
            }
        }
    }

    private fun sendDeviceInformation(context: Context) {
        if (currentOrganizationId != "" && storedData.isNotEmpty() && getSessionId(context) != "") {
            val jsonInfo = JSONObject(storedData)
            val queue = Volley.newRequestQueue(context)
            val stringRequest = object : StringRequest(
                Method.POST,
                currentURL,
                Response.Listener {},
                Response.ErrorListener {
                    if (retryCount < retryMax) {
                        retryCount += 1
                        Handler(Looper.getMainLooper()).postDelayed({
                            sendDeviceInformation(context)
                        }, (minimumDelay * 1000).toLong())
                    }
                }) {
                override fun getBodyContentType(): String {
                    return "application/json; charset=utf-8"
                }

                override fun getBody(): ByteArray {
                    return jsonInfo.toString().toByteArray()
                }

                override fun getHeaders(): Map<String, String> {
                    val params: MutableMap<String, String> = HashMap()
                    params["X-UOW"] = "-risk-api"
                    return params
                }
            }
            queue.add(stringRequest)
        }
    }

    private fun gatherDeviceInformation(c: Context) {
        val jsonMap: MutableMap<String, Any?> = mutableMapOf()
        jsonMap["organizationId"] = currentOrganizationId
        jsonMap["sessionId"] = getSessionId(c)

        val mobileApplication: MutableMap<String, Any?> = mutableMapOf()
        mobileApplication["crossApplicationUniqueId"] = getAppUniqueId()

        val application: MutableMap<String, Any?> = mutableMapOf()
        val pInfo = c.packageManager.getPackageInfo(c.packageName, 0)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        val installationDate = dateFormat.format(Date(pInfo.firstInstallTime))
        application["installationDate"] = installationDate
        application["namespace"] = pInfo.packageName
        application["version"] = pInfo.versionName
        try {
            val appName = c.packageManager.getApplicationLabel(
                c.packageManager.getApplicationInfo(
                    application["namespace"].toString(),
                    PackageManager.GET_META_DATA
                )
            ) as String
            application["name"] = appName
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.i(TAG,e.localizedMessage)
        }
        try {
            val androidId =
                Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID)
            application["androidId"] = androidId
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.i(TAG,e.localizedMessage)
        }
        try {
            val adInfo = AdvertisingIdClient.getAdvertisingIdInfo(c.applicationContext).id
            application["advertisingId"] = adInfo
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.i(TAG,e.localizedMessage)
        }
        mobileApplication["application"] = application

        val operativeSystem: MutableMap<String, Any?> = mutableMapOf()
        operativeSystem["version"] = Build.VERSION.RELEASE
        operativeSystem["apiLevel"] = Build.VERSION.SDK_INT
        operativeSystem["id"] = Build.DISPLAY
        operativeSystem["name"] = "Android"
        mobileApplication["operativeSystem"] = operativeSystem

        val device: MutableMap<String, Any?> = mutableMapOf()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            device["name"] =
                Settings.Global.getString(c.contentResolver, Settings.Global.DEVICE_NAME)
        }
        device["model"] = Build.MODEL
        device["battery"] = batteryStatus(c)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            device["language"] = Locale.getDefault().toLanguageTag()
        } else {
            device["language"] = Locale.getDefault()
        }

        val screen: MutableMap<String, Any?> = mutableMapOf()

        mobileApplication["device"] = device

        screen["resolution"] = getScreenResolution(c)
        screen["orientation"] = getOrientation()
        mobileApplication["screen"] = screen

        val hardware: MutableMap<String, Any?> = mutableMapOf()

        try {
            hardware["cpuArchitecture"] = System.getProperty("os.arch")
            hardware["cpuCores"] = Runtime.getRuntime().availableProcessors()
            val sensorManager = c.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            hardware["sensors"] = sensorManager.getSensorList(Sensor.TYPE_ALL).map { it.name }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.i(TAG,e.localizedMessage)
        }

        try {
            hardware["wifiAvailable"] =
                Settings.Global.getString(c.contentResolver, Settings.Global.WIFI_ON) == "1"
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.i(TAG,e.localizedMessage)
        }
        try {
            hardware["multitouchAvailable"] =
                c.packageManager.hasSystemFeature("android.hardware.touchscreen.multitouch")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.i(TAG,e.localizedMessage)
        }

        hardware["installedApps"] = getInstalledApps(c, true)

        mobileApplication["hardware"] = hardware

        val connectivity: MutableMap<String, Any?> = mutableMapOf()
        val ipAddresses: MutableMap<String, Any?> = mutableMapOf()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(c, Manifest.permission.ACCESS_WIFI_STATE) ==
                PERMISSION_GRANTED
            ) {
                val wifiManager: WifiManager =
                    c.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                try {
                    ipAddresses["wireless"] = wifiManager.connectionInfo.ipAddress
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.i(TAG,e.localizedMessage)
                }
            }
        }
        connectivity["ipAddresses"] = ipAddresses

        val propLines = readGetprop()
        if (propLines?.get("gsm.network.type") != null) {
            connectivity["networkType"] = propLines["gsm.network.type"]
        }
        if (propLines?.get("gsm.sim.operator.alpha") != null) {
            connectivity["isp"] = propLines["gsm.sim.operator.alpha"]
        }

        mobileApplication["connectivity"] = connectivity
        jsonMap["mobileApplication"] = mobileApplication
        jsonMap.values.removeAll(sequenceOf(null))
        storedData = jsonMap

        getLastLocation(c)
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocation(context: Context) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        if (ActivityCompat.checkSelfPermission(context, ACCESS_FINE_LOCATION)
            == PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(context, ACCESS_COARSE_LOCATION)
            == PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val gcd = Geocoder(context, Locale.getDefault())
                        val addresses: List<Address> =
                            gcd.getFromLocation(location.latitude, location.longitude, 1)
                        val address = addresses.first()
                        val mobileApplication = storedData["mobileApplication"] as MutableMap<String, Any?>
                        val connectivity = mobileApplication["connectivity"] as MutableMap<String, Any?>
                        connectivity["country"] = address.countryName
                        connectivity["region"] = address.adminArea
                        connectivity["city"] = address.subAdminArea
                        connectivity["latitude"] = location.latitude
                        connectivity["longitude"] = location.longitude
                    }
                    sendDeviceInformation(context)
                }.addOnFailureListener {
                    sendDeviceInformation(context)
                }
        } else {
            sendDeviceInformation(context)
        }
    }

    private fun getInstalledApps(context: Context, getSysPackages: Boolean): List<String> {
        val res = ArrayList<String>()
        val packs = context.packageManager.getInstalledPackages(0)
        for (i in packs.indices) {
            val p = packs[i]
            val a = p.applicationInfo
            if (!getSysPackages && a.flags and ApplicationInfo.FLAG_SYSTEM == 1) {
                continue
            }
            res.add(p.packageName + ", v: " + p.versionName)
        }
        return res
    }

    private fun batteryStatus(c: Context): MutableMap<String, Any?> {
        val batteryStatus = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            c.registerReceiver(null, ifilter)
        }
        val battery: MutableMap<String, Any?> = mutableMapOf()
        try {
            when (batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> battery["status"] = "charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> battery["status"] = "discharging"
                BatteryManager.BATTERY_STATUS_FULL -> battery["status"] = "full"
                BatteryManager.BATTERY_STATUS_UNKNOWN -> battery["status"] = "unknown"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> battery["status"] = "discharging"
                else -> {
                    battery["status"] = "discharging"
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.i(TAG,e.localizedMessage)

        }
        try {
            battery["type"] = batteryStatus?.extras?.getString(BatteryManager.EXTRA_TECHNOLOGY)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.i(TAG,e.localizedMessage)
        }
        try {
            battery["level"] = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.i(TAG,e.localizedMessage)
        }
        return battery
    }

    private fun getAppUniqueId(): String {
        return hashString(Build.VERSION.RELEASE +
                Build.VERSION.SDK_INT +
                Build.DISPLAY +
                Build.MODEL +
                System.getProperty("os.arch") +
                Runtime.getRuntime().availableProcessors())
    }

    /**
     * Hashing Util
     * @author Sam Clarke <www.samclarke.com>
     * @license MIT
     */
    private fun hashString( input: String): String {
        val HEX_CHARS = "0123456789ABCDEF"
        val bytes = MessageDigest
            .getInstance("SHA-256")
            .digest(input.toByteArray())

        val result = StringBuilder(bytes.size * 2)

        bytes.forEach {
            val i = it.toInt()
            result.append(HEX_CHARS[i shr 4 and 0x0f])
            result.append(HEX_CHARS[i and 0x0f])
        }
        return result.toString()
    }

    private fun readGetprop(): Map<String, String>? {
        try {
            val process = Runtime.getRuntime().exec("getprop")
            val reader = BufferedReader(
                InputStreamReader(process.inputStream)
            )
            var read: Int
            val buffer = CharArray(4096)
            val output = StringBuffer()
            while (reader.read(buffer).also { read = it } > 0) {
                output.append(buffer, 0, read)
            }
            reader.close()
            process.waitFor()
            return output.toString().lines().map { it.drop(1) }.map { it.dropLast(1) }
                .map { it.split("]: [") }
                .map { it.first() to it.last() }
                .toMap()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.i(TAG,e.localizedMessage)
        }
        return null
    }

    private fun getOrientation(): String {
        return if (Resources.getSystem().configuration.orientation == 1) {
            "portrait"
        } else {
            "landscape"
        }
    }

    private fun getScreenResolution(context: Context): String {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            val realSize = Point()
            Display::class.java.getMethod("getRealSize", Point::class.java)
                .invoke(wm.defaultDisplay, realSize)
            return "${realSize.x}x${realSize.y}"
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.i(TAG,e.localizedMessage)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = wm.currentWindowMetrics
            val insets = windowMetrics.windowInsets.getInsets(WindowInsets.Type.statusBars())
            "${windowMetrics.bounds.width() - insets.left - insets.right}x${windowMetrics.bounds.height() + insets.top + insets.bottom}"
        } else {
            val display = wm.defaultDisplay
            val point = Point()
            display.getRealSize(point)
            "${point.x}x${point.y}"
        }
    }
}
