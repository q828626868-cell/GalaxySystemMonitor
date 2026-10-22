package com.galaxy.systemmonitor.data.source

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.galaxy.systemmonitor.data.model.CellularGeneration
import com.galaxy.systemmonitor.data.model.CellularStatus
import com.galaxy.systemmonitor.data.model.NetworkInfo
import com.galaxy.systemmonitor.data.model.NetworkTransport
import com.galaxy.systemmonitor.data.model.Reading
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Connection state from ConnectivityManager (ACCESS_NETWORK_STATE, a normal permission).
 * Never sends any traffic: "validated" is the system's own judgement, not a measurement.
 */
class NetworkReader(private val context: Context) {

    fun hasPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun read(): Reading<NetworkInfo> {
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return Reading.Unavailable("無法取得 ConnectivityManager")
        return try {
            val network = cm.activeNetwork
                ?: return Reading.Ok(
                    NetworkInfo(
                        connected = false,
                        transport = NetworkTransport.NONE,
                        viaVpn = false,
                        hasInternetCapability = null,
                        validated = null,
                        metered = null,
                        cellular = CellularGeneration(CellularStatus.NOT_CELLULAR),
                    ),
                )
            val caps = cm.getNetworkCapabilities(network)
                ?: return Reading.Unavailable("系統未提供目前網路的能力資訊")

            val transport = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.ETHERNET
                caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> NetworkTransport.BLUETOOTH
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkTransport.VPN
                else -> NetworkTransport.OTHER
            }
            val cellular = if (transport == NetworkTransport.CELLULAR) {
                readCellularGeneration()
            } else {
                CellularGeneration(CellularStatus.NOT_CELLULAR)
            }
            Reading.Ok(
                NetworkInfo(
                    connected = true,
                    transport = transport,
                    viaVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                    hasInternetCapability = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                    cellular = cellular,
                ),
            )
        } catch (e: SecurityException) {
            Reading.Unavailable("權限不足，無法讀取網路狀態")
        } catch (e: Exception) {
            Reading.Unavailable("讀取網路狀態失敗：${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun readCellularGeneration(): CellularGeneration {
        if (!hasPhonePermission()) return CellularGeneration(CellularStatus.PERMISSION_REQUIRED)
        return try {
            val base = context.getSystemService(TelephonyManager::class.java)
                ?: return CellularGeneration(CellularStatus.UNKNOWN)
            val subId = SubscriptionManager.getDefaultDataSubscriptionId()
            val tm = if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                base.createForSubscriptionId(subId)
            } else {
                base
            }
            val override = readDisplayOverride(tm)
            val label = CellularLabels.label(tm.dataNetworkType, override)
            if (label != null) {
                CellularGeneration(CellularStatus.KNOWN, label)
            } else {
                CellularGeneration(CellularStatus.UNKNOWN)
            }
        } catch (_: SecurityException) {
            CellularGeneration(CellularStatus.PERMISSION_REQUIRED)
        } catch (_: Exception) {
            CellularGeneration(CellularStatus.UNKNOWN)
        }
    }

    /**
     * One-shot read of TelephonyDisplayInfo (needed to tell 5G NSA apart from LTE).
     * The callback is unregistered immediately after the first value or after a timeout.
     */
    private suspend fun readDisplayOverride(tm: TelephonyManager): Int? =
        withTimeoutOrNull(1_500) {
            suspendCancellableCoroutine { cont ->
                val callback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                    override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                        tm.unregisterTelephonyCallback(this)
                        if (cont.isActive) cont.resume(info.overrideNetworkType)
                    }
                }
                try {
                    tm.registerTelephonyCallback(context.mainExecutor, callback)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                    return@suspendCancellableCoroutine
                }
                cont.invokeOnCancellation { tm.unregisterTelephonyCallback(callback) }
            }
        }
}

object CellularLabels {
    // TelephonyManager.NETWORK_TYPE_* / TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_* values.
    fun label(networkType: Int, overrideType: Int?): String? {
        when (overrideType) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> return "5G+（NR Advanced）"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> return "5G（NSA）"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> return "4G LTE-A Pro"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> return "4G LTE-A"
        }
        return when (networkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G（SA）"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_GSM -> "2G"
            TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN（經由 Wi-Fi）"
            else -> null
        }
    }
}
