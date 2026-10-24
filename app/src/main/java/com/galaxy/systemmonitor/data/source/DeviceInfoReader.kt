package com.galaxy.systemmonitor.data.source

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import com.galaxy.systemmonitor.data.model.DeviceInfo
import com.galaxy.systemmonitor.data.model.DeviceLive
import com.galaxy.systemmonitor.data.model.DisplayInfo
import com.galaxy.systemmonitor.data.model.ThermalStatus
import com.galaxy.systemmonitor.domain.CpuTopology
import com.galaxy.systemmonitor.domain.DeviceNames
import java.io.File

/**
 * Device, CPU, GPU and display facts. No permission required. Every value that a device
 * does not expose (or that SELinux blocks) is returned as null rather than guessed.
 * Blocking calls; run off the main thread.
 */
class DeviceInfoReader(private val context: Context) {

    private val cpuDir = File("/sys/devices/system/cpu")
    private val cores: List<Int> by lazy {
        CpuTopology.parseCoreList(readText(File(cpuDir, "possible")))
            .ifEmpty { (0 until Runtime.getRuntime().availableProcessors()).toList() }
    }

    fun readStatic(): DeviceInfo {
        val socManufacturer = Build.SOC_MANUFACTURER.takeUnless { it.isBlank() || it == Build.UNKNOWN }
        val socModel = Build.SOC_MODEL.takeUnless { it.isBlank() || it == Build.UNKNOWN }
        val gl = readGl()
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            marketName = DeviceNames.marketName(Build.MANUFACTURER, Build.MODEL),
            deviceName = try {
                Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            } catch (e: Exception) {
                null
            },
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH.takeUnless { it.isBlank() },
            oneUiVersion = DeviceNames.oneUiVersion(systemProperty("ro.build.version.oneui")),
            buildNumber = Build.DISPLAY,
            baseband = Build.getRadioVersion()?.takeUnless { it.isBlank() },
            kernel = System.getProperty("os.version")?.takeUnless { it.isBlank() },
            abis = Build.SUPPORTED_ABIS.toList(),
            socManufacturer = socManufacturer,
            socModel = socModel,
            socName = socModel?.let { DeviceNames.socName(Build.MANUFACTURER, it) },
            coreCount = cores.size,
            clusters = CpuTopology.clusters(
                maxMhz = cores.associateWith { freq(it, "cpuinfo_max_freq") },
                minMhz = cores.associateWith { freq(it, "cpuinfo_min_freq") },
            ),
            gpuRenderer = gl?.renderer,
            gpuVendor = gl?.vendor,
            openGlVersion = gl?.version,
            vulkanVersion = vulkanVersion(),
            display = readDisplay(),
        )
    }

    fun readLive(): DeviceLive {
        val current = cores.associateWith { freq(it, "scaling_cur_freq") }
        val pm = context.getSystemService(PowerManager::class.java)
        val headroom = try {
            // NaN when unsupported or when called more often than once per second.
            pm?.getThermalHeadroom(HEADROOM_FORECAST_SECONDS)?.takeUnless { it.isNaN() || it < 0f }
        } catch (e: Exception) {
            null
        }
        return DeviceLive(
            coreMhz = current,
            coreFrequencyReadable = current.values.any { it != null },
            thermalStatus = when (pm?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalStatus.NONE
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.LIGHT
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL -> ThermalStatus.CRITICAL
                PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalStatus.EMERGENCY
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.SHUTDOWN
                else -> ThermalStatus.UNKNOWN
            },
            thermalHeadroom = headroom,
            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
            awakeMillis = SystemClock.uptimeMillis(),
        )
    }

    private fun freq(core: Int, file: String): Int? =
        CpuTopology.khzToMhz(readText(File(cpuDir, "cpu$core/cpufreq/$file")))

    private fun readText(file: File): String? = try {
        file.readText()
    } catch (e: Exception) {
        // Missing for offline cores; "Permission denied" where SELinux blocks app access.
        null
    }

    /** android.os.SystemProperties is not in the SDK; read via reflection and treat any failure as unavailable. */
    @SuppressLint("PrivateApi")
    private fun systemProperty(key: String): String? = try {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
            .invoke(null, key) as? String
    } catch (e: Exception) {
        null
    }?.takeUnless { it.isBlank() }

    private fun vulkanVersion(): String? {
        val feature = context.packageManager.systemAvailableFeatures
            .firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION } ?: return null
        val v = feature.version
        // VK_MAKE_API_VERSION: major in bits 22-28, minor in bits 12-21.
        return "${(v shr 22) and 0x7F}.${(v shr 12) and 0x3FF}"
    }

    private fun readDisplay(): DisplayInfo? = try {
        // context.display throws for the application context, so ask DisplayManager for the built-in screen.
        val display: Display? = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        display?.let { d ->
            val mode = d.mode
            val hdr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                mode.supportedHdrTypes.toList()
            } else {
                @Suppress("DEPRECATION")
                d.hdrCapabilities?.supportedHdrTypes?.toList().orEmpty()
            }
            DisplayInfo(
                widthPx = minOf(mode.physicalWidth, mode.physicalHeight),
                heightPx = maxOf(mode.physicalWidth, mode.physicalHeight),
                densityDpi = context.resources.displayMetrics.densityDpi,
                currentRefreshHz = d.refreshRate,
                supportedRefreshHz = d.supportedModes
                    .filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
                    .map { it.refreshRate }
                    .distinct()
                    .sorted(),
                hdrTypes = hdr.distinct().mapNotNull(::hdrName),
            )
        }
    } catch (e: Exception) {
        null
    }

    private fun hdrName(type: Int): String? = when (type) {
        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
        Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
        Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
        Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
        else -> null
    }

    private data class GlInfo(val renderer: String?, val vendor: String?, val version: String?)

    /** Creates a throwaway 1×1 offscreen GLES context just to ask for the GPU strings. */
    private fun readGl(): GlInfo? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null
        var context = EGL14.EGL_NO_CONTEXT
        var surface = EGL14.EGL_NO_SURFACE
        return try {
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            val attribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE,
            )
            if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) || count[0] == 0) return null
            context = EGL14.eglCreateContext(
                display, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
            )
            surface = EGL14.eglCreatePbufferSurface(
                display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
            )
            if (context == EGL14.EGL_NO_CONTEXT || surface == EGL14.EGL_NO_SURFACE) return null
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return null
            GlInfo(
                renderer = GLES20.glGetString(GLES20.GL_RENDERER),
                vendor = GLES20.glGetString(GLES20.GL_VENDOR),
                version = GLES20.glGetString(GLES20.GL_VERSION),
            )
        } catch (e: Exception) {
            null
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }

    private companion object {
        const val HEADROOM_FORECAST_SECONDS = 10
    }
}
