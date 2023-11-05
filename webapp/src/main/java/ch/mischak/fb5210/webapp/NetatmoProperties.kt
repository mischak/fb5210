/*
 * MIT License
 *
 * Copyright (c) 2023 Mischa Kölliker
 */
package ch.mischak.fb5210.webapp

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.PrintStream
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import javax.annotation.PostConstruct

@Component
class NetatmoProperties {
    val log = LoggerFactory.getLogger(this.javaClass.name)

    val properties = Properties()

    @PostConstruct
    @Synchronized
    fun init() {
        try {
            properties.load(FileInputStream(propertiesPath()))
        } catch (e: FileNotFoundException) {
            log.error("init(): netatmo.properties not found: $e")
        }
    }

    var last_seen_oben : LocalDateTime?
        get() = properties.getProperty("last_seen_oben")?.let { LocalDateTime.parse(it) }
        set(v) = setProperty("last_seen_oben", v?.toString())

    var temp_oben : BigDecimal?
        get() = properties.getProperty("temp_oben")?.toBigDecimal()
        set(v) = setProperty("temp_oben", v)

    var humidity_oben : Int?
        get() = properties.getProperty("humidity_oben")?.toInt()
        set(v) = setProperty("humidity_oben", v)



    var last_seen_unten : LocalDateTime?
        get() = properties.getProperty("last_seen_unten")?.let { LocalDateTime.parse(it) }
        set(v) = setProperty("last_seen_unten", v?.toString())

    var temp_unten : BigDecimal?
        get() = properties.getProperty("temp_unten")?.toBigDecimal()
        set(v) = setProperty("temp_unten", v)

    var humidity_unten : Int?
        get() = properties.getProperty("humidity_unten")?.toInt()
        set(v) = setProperty("humidity_unten", v)

    var battery_unten : Int?
        get() = properties.getProperty("battery_unten")?.toInt()
        set(v) = setProperty("battery_unten", v)


    var last_retrieved : LocalDateTime?
        get() = properties.getProperty("last_retrieved")?.let { LocalDateTime.parse(it) }
        set(v) = setProperty("last_retrieved", v?.toString())


    var netatmo_access_token : String?
        get() = properties.getProperty("netatmo_access_token")
        set(v) = setProperty("netatmo_access_token", v)

    var netatmo_refresh_token : String
        get() = properties.getProperty("netatmo_refresh_token")
        set(v) = setProperty("netatmo_refresh_token", v)

    var netatmo_client_secret : String
        get() = properties.getProperty("netatmo_client_secret")
        set(v) = setProperty("netatmo_client_secret", v)

    var netatmo_client_id : String
        get() = properties.getProperty("netatmo_client_id")
        set(v) = setProperty("netatmo_client_id", v)


    fun dumpFile(out : PrintStream) {
        Properties().apply { load(FileInputStream(propertiesPath())) }.list(out)
    }

    @Synchronized
    private fun setProperty(name : String, value : Any?) {
        if (value != null)
            properties.setProperty(name, value.toString())
        else
            properties.remove(name)

        try {
            properties.store(FileOutputStream(propertiesPath()), "")
        } catch (e: Exception) {
            log.error("setProperty(): Problem storing properties file $e")
        }
    }

    private fun propertiesPath() = System.getProperty("user.dir") + "/netatmo.properties"
}
