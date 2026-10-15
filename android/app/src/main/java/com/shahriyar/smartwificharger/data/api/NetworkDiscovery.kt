package com.shahriyar.smartwificharger.data.api

import android.content.Context
import android.net.wifi.WifiManager
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.FileReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

data class DiscoveredDevice(
    val ip: String,
    val baseUrl: String,
    val deviceId: String,
    val deviceName: String,
    val firmware: String,
    val discoveryMethod: String
)

/**
 * Ultra-fast Multi-Mechanism LAN & AP Discovery for Smart Charger ESP8266.
 *
 * Mechanisms:
 *  1. UDP Broadcast Discovery (port 8266: DISCOVER_SMART_CHARGER) with MulticastLock
 *  2. ESP8266 Direct AP Gateway (http://192.168.4.1)
 *  3. mDNS Hostname (http://smartcontroller.local, http://smartcharger.local)
 *  4. Cached / Configured IP & Learned Firebase IP
 *  5. ARP Cache (/proc/net/arp)
 *  6. Fast Parallel Subnet Sweep
 *
 * Device identity verification:
 * Checks for "deviceType": "SMART_CHARGER_ESP8266" or matching deviceId/firmware.
 */
object NetworkDiscovery {
    const val UDP_DISCOVERY_PORT = 8266
    const val DISCOVERY_PAYLOAD = "DISCOVER_SMART_CHARGER"

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(500, TimeUnit.MILLISECONDS)
        .readTimeout(500, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun discoverLocalEsp(
        cachedIp: String? = null,
        extraIps: List<String> = emptyList(),
        network: android.net.Network? = null,
        socketFactory: SocketFactory? = null,
        context: Context? = null
    ): String? = withContext(Dispatchers.IO) {
        val details = discoverDeviceDetails(cachedIp, extraIps, network, socketFactory, context)
        details?.baseUrl
    }

    suspend fun discoverDeviceDetails(
        cachedIp: String? = null,
        extraIps: List<String> = emptyList(),
        network: android.net.Network? = null,
        socketFactory: SocketFactory? = null,
        context: Context? = null
    ): DiscoveredDevice? = withContext(Dispatchers.IO) {
        // ── Mechanism 1: Fast UDP Broadcast Discovery (600ms timeout) with MulticastLock & Network Binding ──
        val udpResult = withTimeoutOrNull(600L) {
            discoverViaUdp(timeoutMs = 500, network = network, context = context)
        }
        if (udpResult != null) return@withContext udpResult

        // ── Mechanisms 2-6: Parallel HTTP Endpoint Candidate Probing ──
        val candidates = buildCandidateList(cachedIp, extraIps)
        val client = when {
            network != null -> baseClient.newBuilder()
                .socketFactory(network.socketFactory)
                .dns(object : okhttp3.Dns {
                    override fun lookup(hostname: String): List<java.net.InetAddress> {
                        return try {
                            network.getAllByName(hostname).toList()
                        } catch (_: Exception) {
                            try {
                                okhttp3.Dns.SYSTEM.lookup(hostname)
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }
                })
                .build()
            socketFactory != null -> baseClient.newBuilder().socketFactory(socketFactory).build()
            else -> baseClient
        }

        coroutineScope {
            val jobs = candidates.map { url ->
                async { verifyEspIdentity(client, url) }
            }
            jobs.awaitAll().firstOrNull { it != null }
        }
    }

    /**
     * Broadcasts UDP packet "DISCOVER_SMART_CHARGER" to 255.255.255.255:8266 and interface broadcasts.
     * Uses MulticastLock on Android to prevent Wi-Fi chipset dropping incoming broadcast replies.
     * Uses network.bindSocket(socket) to force broadcast over the Wi-Fi interface.
     */
    fun discoverViaUdp(
        timeoutMs: Int = 450,
        network: android.net.Network? = null,
        context: Context? = null
    ): DiscoveredDevice? {
        val wifiManager = context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = try {
            wifiManager?.createMulticastLock("SmartChargerUdp")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) { null }

        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = timeoutMs
            }

            // CRITICAL: Bind socket to the active Wi-Fi Network so packet doesn't leak out cellular interface!
            if (network != null) {
                try {
                    network.bindSocket(socket)
                } catch (_: Exception) {}
            }

            val data = DISCOVERY_PAYLOAD.toByteArray(Charsets.UTF_8)
            // 1. Global subnet broadcast
            val packet = DatagramPacket(
                data,
                data.size,
                InetAddress.getByName("255.255.255.255"),
                UDP_DISCOVERY_PORT
            )
            socket.send(packet)

            // 2. Specific interface broadcasts (e.g. 192.168.4.255 or 192.168.1.255)
            try {
                for (bcast in getBroadcastAddresses()) {
                    val p = DatagramPacket(data, data.size, bcast, UDP_DISCOVERY_PORT)
                    socket.send(p)
                }
            } catch (_: Exception) {}

            val buffer = ByteArray(1024)
            val receivePacket = DatagramPacket(buffer, buffer.size)
            socket.receive(receivePacket)

            val reply = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8)
            val json = JsonParser.parseString(reply).asJsonObject

            val devType = json.get("deviceType")?.asString ?: ""
            if (devType.contains("SMART_CHARGER", ignoreCase = true) || reply.contains("SMART_CHARGER")) {
                val ip = json.get("ip")?.asString ?: receivePacket.address.hostAddress ?: ""
                val devId = json.get("deviceId")?.asString ?: "wifi_charger_01"
                val name = json.get("name")?.asString ?: "Smart Charger"
                val fw = json.get("firmware")?.asString ?: "5.1.0-PROD"
                val baseUrl = if (ip.startsWith("http")) ip else "http://$ip"

                DiscoveredDevice(
                    ip = ip,
                    baseUrl = baseUrl,
                    deviceId = devId,
                    deviceName = name,
                    firmware = fw,
                    discoveryMethod = "UDP_BROADCAST"
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            try { socket?.close() } catch (_: Exception) {}
            try {
                if (multicastLock != null && multicastLock.isHeld) {
                    multicastLock.release()
                }
            } catch (_: Exception) {}
        }
    }

    private fun getBroadcastAddresses(): List<InetAddress> {
        val list = mutableListOf<InetAddress>()
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return list
            for (iface in ifaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (ifaceAddr in iface.interfaceAddresses) {
                    val bcast = ifaceAddr.broadcast
                    if (bcast != null) list.add(bcast)
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun buildCandidateList(cachedIp: String?, extraIps: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        val list = mutableListOf<String>()

        fun add(url: String) {
            val normalised = if (url.startsWith("http")) url else "http://$url"
            val clean = if (normalised.endsWith("/")) normalised.substring(0, normalised.length - 1) else normalised
            if (seen.add(clean)) list.add(clean)
        }

        // Priority 1: ESP8266 SoftAP default IP
        add("http://192.168.4.1")

        // Priority 2: mDNS Hostnames (Standard smartcontroller and legacy smartcharger)
        add("http://smartcontroller.local")
        add("http://smartcharger.local")

        // Priority 3: User configured / cached local IP
        if (!cachedIp.isNullOrBlank()) add(cachedIp)

        // Priority 4: Extra candidate IPs (e.g. reported by Firebase info.ip)
        extraIps.forEach { if (it.isNotBlank()) add(it) }

        // Priority 5: ARP cache IPs
        getArpIps().forEach { add(it) }

        // Priority 6: Dynamic Subnet scan on active Wi-Fi interface
        val subnetPrefix = getLocalSubnetPrefix()
        if (subnetPrefix != null) {
            for (i in 2..32) {
                add("http://$subnetPrefix.$i")
            }
            for (i in 100..130) {
                add("http://$subnetPrefix.$i")
            }
            for (i in 200..235) {
                add("http://$subnetPrefix.$i")
            }
        }

        return list
    }

    /**
     * Reads /proc/net/arp on Android Linux kernel to find actively communicating LAN IPs.
     */
    private fun getArpIps(): List<String> {
        val ips = mutableListOf<String>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                reader.readLine() // skip header line
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val tokens = line!!.split("\\s+".toRegex())
                    if (tokens.size >= 4) {
                        val ip = tokens[0]
                        val mac = tokens[3]
                        if (ip.count { it == '.' } == 3 && mac != "00:00:00:00:00:00") {
                            ips.add("http://$ip")
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return ips
    }

    private fun getLocalSubnetPrefix(): String? {
        return try {
            val ifaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
            for (iface in ifaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses.toList()) {
                    if (addr.isLoopbackAddress) continue
                    val ip = addr.hostAddress ?: continue
                    if (ip.contains(':')) continue
                    val parts = ip.split(".")
                    if (parts.size == 4) {
                        return "${parts[0]}.${parts[1]}.${parts[2]}"
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Verifies that the responding HTTP server is genuinely our ESP8266 Smart Charger.
     */
    private fun verifyEspIdentity(client: OkHttpClient, baseUrl: String): DiscoveredDevice? {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/v1/health")
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                val json = JsonParser.parseString(bodyStr).asJsonObject

                val devType = json.get("deviceType")?.asString ?: ""
                val devId = json.get("deviceId")?.asString ?: ""
                val isSmartCharger = devType.contains("SMART_CHARGER", ignoreCase = true) ||
                        devId.contains("charger", ignoreCase = true) ||
                        bodyStr.contains("5.1.0") ||
                        (json.has("ok") && json.get("ok").asBoolean)

                if (isSmartCharger) {
                    val cleanBase = if (baseUrl.endsWith("/")) baseUrl.substring(0, baseUrl.length - 1) else baseUrl
                    val rawIp = cleanBase.replace("http://", "").replace("https://", "").split(":")[0]
                    val espReportedIp = json.get("ip")?.asString
                    val finalIp = if (!espReportedIp.isNullOrBlank() && espReportedIp != "0.0.0.0") espReportedIp else rawIp
                    val finalBaseUrl = if (finalIp.startsWith("http")) finalIp else "http://$finalIp"

                    DiscoveredDevice(
                        ip = finalIp,
                        baseUrl = finalBaseUrl,
                        deviceId = devId.ifBlank { "wifi_charger_01" },
                        deviceName = json.get("name")?.asString ?: "Smart Charger",
                        firmware = json.get("firmware")?.asString ?: "5.1.0-PROD",
                        discoveryMethod = "HTTP_HEALTH_PROBE"
                    )
                } else null
            }
        } catch (_: Exception) {
            // Legacy /api/status fallback
            try {
                val fallbackReq = Request.Builder()
                    .url("$baseUrl/api/status")
                    .header("Cache-Control", "no-cache")
                    .build()
                client.newCall(fallbackReq).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: ""
                        if (body.contains("telemetry") || body.contains("battery_voltage")) {
                            val cleanBase = if (baseUrl.endsWith("/")) baseUrl.substring(0, baseUrl.length - 1) else baseUrl
                            val rawIp = cleanBase.replace("http://", "").replace("https://", "").split(":")[0]
                            DiscoveredDevice(
                                ip = rawIp,
                                baseUrl = cleanBase,
                                deviceId = "wifi_charger_01",
                                deviceName = "Smart Charger",
                                firmware = "5.1.0-PROD",
                                discoveryMethod = "LEGACY_STATUS_PROBE"
                            )
                        } else null
                    } else null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
