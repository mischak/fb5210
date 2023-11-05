/*
 * MIT License
 *
 * Copyright (c) 2023 Mischa Kölliker
 */
package ch.mischak.fb5210.webapp

import ch.mischak.fb5210.webapp.Heizkreis.Companion
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.PrintStream
import java.math.BigDecimal
import java.util.*

class HeizungProperties(val heizkreisFile : Heizkreis) {
    val log = LoggerFactory.getLogger(this.javaClass.name)

    val properties = Properties()

    @Synchronized
    fun init() : HeizungProperties {
        try {
            properties.load(FileInputStream(propertiesPath()))
        } catch (e: FileNotFoundException) {
            heizkreis = heizkreisFile // init properties when file is initially created
            master = master
        }

        return this
    }

    var heizkreis : Heizkreis
        get() = Heizkreis.fromString(getProperty("heizkreis"))
        set(v) = setProperty("heizkreis", v)

    var master : ControllingDevice
        get() = ControllingDevice.fromString(getProperty("master"))
        set(v) = setProperty("master", v)

    var betriebsart : Betriebsart
        get() = Betriebsart.fromString(getProperty("betriebsart"))
        set(v) = setProperty("betriebsart", v)

    var innentemp_soll : BigDecimal?
        get() = getPrimitivesProperty("innentemp_soll")?.toBigDecimal()
        set(v) = setProperty("innentemp_soll", v)

    var innentemp_ist : BigDecimal?
        get() = getPrimitivesProperty("innentemp_ist")?.toBigDecimal()
        set(v) = setProperty("innentemp_ist", v)

    var fake_innen_temp : BigDecimal?
        get() = getPrimitivesProperty("fake_innen_temp")?.toBigDecimal()
        set(v) = setProperty("fake_innen_temp", v)

    var aussentemp : BigDecimal?
        get() = getPrimitivesProperty("aussentemp")?.toBigDecimal()
        set(v) = setProperty("aussentemp", v)

    var wasser_soll : BigDecimal?
        get() = getPrimitivesProperty("wasser_soll")?.toBigDecimal()
        set(v) = setProperty("wasser_soll", v)

    var vorlauf_ziel : BigDecimal?
        get() = getPrimitivesProperty("vorlauf_ziel")?.toBigDecimal()
        set(v) = setProperty("vorlauf_ziel", v)

    var fusspunkt : BigDecimal?
        get() = getPrimitivesProperty("fusspunkt")?.toBigDecimal()
        set(v) = setProperty("fusspunkt", v)

    var steigung : BigDecimal?
        get() = getPrimitivesProperty("steigung")?.toBigDecimal()
        set(v) = setProperty("steigung", v)

    var korrekturfaktor : BigDecimal?
        get() = getPrimitivesProperty("korrekturfaktor")?.toBigDecimal()
        set(v) = setProperty("korrekturfaktor", v)

    var korrekturoffset : BigDecimal?
        get() = getPrimitivesProperty("korrekturoffset")?.toBigDecimal()
        set(v) = setProperty("korrekturoffset", v)

    var temp_compensation : BigDecimal?
        get() = getPrimitivesProperty("temp_compensation")?.toBigDecimal()
        set(v) = setProperty("temp_compensation", v)

    var brenner_status : Boolean?
        get() = getPrimitivesProperty("brenner_status")?.toBoolean()
        set(v) = setProperty("brenner_status", v)

    var umwaelzpumpe_status : Boolean?
        get() = getPrimitivesProperty("umwaelzpumpe_status")?.toBoolean()
        set(v) = setProperty("umwaelzpumpe_status", v)

    var boilerpumpe_status : Boolean?
        get() = getPrimitivesProperty("boilerpumpe_status")?.toBoolean()
        set(v) = setProperty("boilerpumpe_status", v)

    var setze_zeit : Boolean
        get() = getPrimitivesProperty("setze_zeit")?.toBoolean() ?: false
        set(v) = setProperty("setze_zeit", v)


    fun dumpFile(out : PrintStream) {
        Properties().apply { load(FileInputStream(propertiesPath())) }.list(out)
    }

    private fun getProperty(name : String) = properties.getProperty(name)

    private fun getPrimitivesProperty(name: String) : String? = getProperty(name)?.let { if (it.isEmpty()) null else it }

    @Synchronized
    private fun setProperty(name : String, value : Any?) {
        val oldValue : String?
        if (value != null) {
            oldValue = properties.setProperty(name, value.toString()) as String?
        } else {
            oldValue = properties.setProperty(name, "") as String?
        }

        try {
            if (oldValue != value?.toString()) {
                properties.store(FileOutputStream(propertiesPath()), "Last change: $name = $value")
            }
        } catch (e: Exception) {
            log.error("setProperty(): Problem storing properties file $e")
        }
    }

    private fun propertiesPath() = System.getProperty("user.dir") + "/heizung-${heizkreisFile.channelNumber}.properties"
}
