/*
 * MIT License
 *
 * Copyright (c) 2023 Mischa Kölliker
 */
package ch.mischak.fb5210.webapp

import ch.mischak.fb5210.webapp.Heizkreis.OBEN
import ch.mischak.fb5210.webapp.Heizkreis.UNTEN
import org.influxdb.InfluxDB
import org.influxdb.dto.Point
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit
import khttp.get as httpGet
import khttp.post as httpPost

private const val NETATMO_DEVICE_ID_1 = "00:00:00:00:00:00" // TODO: add your own device id
private const val NETATMO_DEVICE_ID_2 = "00:00:00:00:00:00" // TODO: add your own device id

@Component
class NetatmoReceiver {
    val log = LoggerFactory.getLogger(this.javaClass.name)

    @Autowired
    lateinit var netatmoProperties: NetatmoProperties

    @Autowired
    lateinit var heizungProperties : HeizungProperties

    @Autowired
    lateinit var influxDB: InfluxDB

    // initDelay is to avoid the method being executed during tests
    @Scheduled(fixedRate = 600_000, initialDelay = 30_000)
    fun retrieveTemperatures() {
        if (heizungProperties.heizkreis != UNTEN) {
            log.info("retrieveTemperatures(): Skip receiving Netatmo temperatures for heizkreis ${heizungProperties.heizkreis}")
            return
        }

        retrieveTemperatures0()
    }

    internal fun retrieveTemperatures0() {
        log.info("retrieveTemperatures0(): Receiving Netatmo temperatures")

        try {
            val netatmo_refresh_token = netatmoProperties.netatmo_refresh_token
            val netatmo_client_secret = netatmoProperties.netatmo_client_secret
            val netatmo_client_id     = netatmoProperties.netatmo_client_id
            var netatmo_access_token  = netatmoProperties.netatmo_access_token ?: refreshAccessToken(netatmo_refresh_token, netatmo_client_secret, netatmo_client_id)

            var tries = 0
            while (tries++ < 2) {
                val r = httpGet("https://api.netatmo.net/api/getstationsdata?access_token=$netatmo_access_token&device_id=$NETATMO_DEVICE_ID_1")
                if (r.statusCode == 403) {
                    netatmo_access_token = refreshAccessToken(netatmo_refresh_token, netatmo_client_secret, netatmo_client_id)
                } else if (r.statusCode > 200) {
                    log.error("retrieveTemperatures0(): There was an error retrieving Netatmo temperatures. Status-Code: ${r.statusCode}, Body: ${r.text}")
                } else {
                    val resp = r.jsonObject  // org.json.JSONObject

                    val device = resp.getJSONObject("body").getJSONArray("devices").getJSONObject(0)
                    val modules = device.getJSONArray("modules")
                    val dashboard_data_oben = device.getJSONObject("dashboard_data")

                    val zoneId = TimeZone.getDefault().toZoneId()
                    val timestamp_utc = dashboard_data_oben.getLong("time_utc")
                    netatmoProperties.last_seen_oben = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp_utc), zoneId)
                    netatmoProperties.temp_oben = dashboard_data_oben.getString("Temperature").toBigDecimal()
                    netatmoProperties.humidity_oben = dashboard_data_oben.getInt("Humidity")

                    lateinit var moduleUnten : JSONObject
                    for (item in modules) {
                        if (item.getString("_id") == NETATMO_DEVICE_ID_2) {
                            moduleUnten = item
                            break
                        }
                    }

                    val dashboard_data_unten = moduleUnten.getJSONObject("dashboard_data")
                    netatmoProperties.battery_unten = moduleUnten.getInt("battery_percent")
                    netatmoProperties.last_seen_unten = LocalDateTime.ofInstant(Instant.ofEpochSecond(moduleUnten.getLong("last_seen")), zoneId)
                    netatmoProperties.temp_unten = dashboard_data_unten.getString("Temperature").toBigDecimal()
                    netatmoProperties.humidity_unten = dashboard_data_unten.getInt("Humidity")

                    netatmoProperties.last_retrieved = LocalDateTime.now()

                    influxDB.write(Point.measurement("temp")
                            .time(timestamp_utc, TimeUnit.SECONDS)
                            .tag("heizkreis", OBEN.tagName)
                            .addField("net_temp", netatmoProperties.temp_oben)
                            .addField("net_hum", netatmoProperties.humidity_oben)
                            .build()
                    )

                    influxDB.write(Point.measurement("temp")
                            .time(timestamp_utc, TimeUnit.SECONDS)
                            .tag("heizkreis", UNTEN.tagName)
                            .addField("net_temp", netatmoProperties.temp_unten)
                            .addField("net_hum", netatmoProperties.humidity_unten)
                            .addField("net_bat", netatmoProperties.battery_unten)
                            .build()
                    )

                    break
                }
            }

        } catch (e: IllegalStateException) {
            log.error("retrieveTemperatures0(): Is either the netatmo_refresh_token, client_id, or the netatmo_client_secret missing in the properties file?", e)
        } catch (e: Exception) {
            log.error("retrieveTemperatures0(): Error while retrieving Netatmo temperatures", e)
        }
    }

    private fun refreshAccessToken(netatmo_refresh_token: String, netatmo_client_secret: String, netatmo_client_id: String): String {
        log.info("refreshAccessToken(): Refreshing access_token")

        val r = httpPost(
                "https://api.netatmo.net/oauth2/token",
                data = mapOf(
                        "refresh_token" to netatmo_refresh_token,
                        "client_secret" to netatmo_client_secret,
                        "client_id" to netatmo_client_id,
                        "grant_type" to "refresh_token"
                )
        )

        if (r.statusCode == 200) {
            val new_access_token = r.jsonObject.getString("access_token")
            netatmoProperties.netatmo_access_token = new_access_token
            return new_access_token
        } else {
            log.error("refreshAccessToken(): Error while refreshing access token. Status-Code: ${r.statusCode}, Body: ${r.text}")
            return ""
        }
    }
}
