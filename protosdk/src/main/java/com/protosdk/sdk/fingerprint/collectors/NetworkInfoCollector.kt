package com.protosdk.sdk.fingerprint.collectors

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import com.protosdk.sdk.fingerprint.interfaces.BaseCollector
import org.json.JSONArray
import org.json.JSONObject

class NetworkInfoCollector : BaseCollector() {
  override suspend fun collect(context: Context): JSONObject = safeCollect {
    val cm =
      context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return@safeCollect JSONObject().put("error", "connectivityUnavailable")

    val active = cm.activeNetwork
    val caps = active?.let { cm.getNetworkCapabilities(it) }
    val link = active?.let { cm.getLinkProperties(it) }

    JSONObject().apply {
      collectDataPoint("isCaptivePortal") {
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true
      }
      collectDataPoint("isRoaming") { caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING) == false }
      collectDataPoint("isVpn") { caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true || link.isTun() }

      collectDataPoint("transports") { JSONArray().apply { caps?.let { addTransports(it, this) } } }

      collectDataPoint("interfaceName") { link?.interfaceName ?: JSONObject.NULL }
      val dnsList: List<String> = link?.dnsServers
        ?.mapNotNull { it.hostAddress }
        ?.filter { it.isNotBlank() }
        ?: emptyList()
      collectDataPoint("dnsServers") { JSONArray(dnsList) }
      collectDataPoint("privateDnsActive") { link?.isPrivateDnsActive ?: JSONObject.NULL }
      collectDataPoint("privateDnsServerName") { link?.privateDnsServerName ?: JSONObject.NULL }
    }
  }

  private fun addTransports(caps: NetworkCapabilities, into: JSONArray) {
    val transports =
      listOf(
        NetworkCapabilities.TRANSPORT_WIFI to "wifi",
        NetworkCapabilities.TRANSPORT_CELLULAR to "cellular",
        NetworkCapabilities.TRANSPORT_ETHERNET to "ethernet",
        NetworkCapabilities.TRANSPORT_VPN to "vpn",
        NetworkCapabilities.TRANSPORT_BLUETOOTH to "bluetooth",
        NetworkCapabilities.TRANSPORT_LOWPAN to "lowpan",
        NetworkCapabilities.TRANSPORT_WIFI_AWARE to "wifi_aware",
        NetworkCapabilities.TRANSPORT_USB to "usb",
        NetworkCapabilities.TRANSPORT_SATELLITE to "satellite",
      )
    transports.forEach { (id, name) -> if (caps.hasTransport(id)) into.put(name) }
  }

  private fun LinkProperties?.isTun(): Boolean {
    val iface = this?.interfaceName ?: return false
    return iface.startsWith("tun") || iface.startsWith("ppp") || iface.startsWith("wg")
  }

  override fun getCollectorName(): String = "NetworkInfoCollector"

  override fun getRequiredPermissions(): List<String> = listOf(android.Manifest.permission.ACCESS_NETWORK_STATE)

  override fun hasRequiredPermissions(context: Context): Boolean = true
}
