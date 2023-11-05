/*
 * MIT License
 *
 * Copyright (c) 2023 Mischa Kölliker
 */
package ch.mischak.fb5210.webapp

import ch.mischak.fb5210.webapp.Betriebsart.AUTOMATIK
import ch.mischak.fb5210.webapp.ControllingDevice.RASPI
import ch.mischak.fb5210.webapp.Heizkreis.OBEN
import junit.framework.Assert.assertEquals
import org.influxdb.InfluxDB
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit4.SpringRunner
import java.io.ByteArrayOutputStream
import java.io.PrintStream

@RunWith(SpringRunner::class)
@SpringBootTest
class HeizungPropertiesTest {

    @MockBean
    lateinit var telegramHandler: TelegramHandler

    @MockBean
    lateinit var influxDB: InfluxDB

    @Autowired
    lateinit var heizungProperties : HeizungProperties

    @Test
    fun testPropertiesFileAccess() {
        println(heizungProperties.master)
    }

    @Test
    fun testPropertiesContent() {
        heizungProperties.master = RASPI
        heizungProperties.betriebsart = AUTOMATIK
        heizungProperties.heizkreis = OBEN
        heizungProperties.innentemp_ist = 29.5.toBigDecimal()
        heizungProperties.innentemp_soll = 5.5.toBigDecimal()
        heizungProperties.wasser_soll = 50.toBigDecimal()

        heizungProperties.fusspunkt = 35.toBigDecimal()
        heizungProperties.fusspunkt = null
        println(heizungProperties.fusspunkt)

        val byteArrayOutputStream = ByteArrayOutputStream()
        val printStream = PrintStream(byteArrayOutputStream)
        heizungProperties.dumpFile(printStream)
        printStream.flush()
        println(byteArrayOutputStream.toString())

        val lf = System.lineSeparator()
        assertEquals("-- listing properties --${lf}fusspunkt=${lf}heizkreis=OBEN${lf}master=RASPI${lf}wasser_soll=50${lf}innentemp_ist=29.5${lf}innentemp_soll=5.5${lf}betriebsart=AUTOMATIK${lf}", byteArrayOutputStream.toString())
    }
}
