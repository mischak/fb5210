/*
 * MIT License
 *
 * Copyright (c) 2023 Mischa Kölliker
 */
package ch.mischak.fb5210.webapp

import ch.mischak.fb5210.crc.CrcCalculator
import ch.mischak.fb5210.webapp.Betriebsart.AUTOMATIK
import ch.mischak.fb5210.webapp.Betriebsart.Companion
import ch.mischak.fb5210.webapp.Betriebsart.FIX_TAG
import ch.mischak.fb5210.webapp.ControllingDevice.BEDIENMODUL
import ch.mischak.fb5210.webapp.ControllingDevice.RASPI
import ch.mischak.fb5210.webapp.Heizkreis.OBEN
import ch.mischak.fb5210.webapp.Heizkreis.UNTEN
import gnu.io.CommPort
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newCoroutineContext
import org.apache.commons.lang3.StringUtils.leftPad
import org.apache.commons.lang3.StringUtils.rightPad
import org.influxdb.InfluxDB
import org.influxdb.dto.Point
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.Integer.toHexString
import java.lang.Thread.currentThread
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField.*
import java.time.temporal.ChronoUnit.MILLIS
import java.util.*
import java.util.concurrent.TimeUnit.MILLISECONDS
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy


@Service
open class TelegramHandler {
    val log = LoggerFactory.getLogger(this.javaClass.name)

    // Heizkreise:
    // 0: oben
    // 1: unten

    private val TIME_FORMATTER = DateTimeFormatterBuilder()
            .appendValue(DAY_OF_MONTH, 2)
            .appendLiteral('.')
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendFraction(NANO_OF_SECOND, 0, 9, true)
            .toFormatter()

    @Autowired
    lateinit var properties : HeizungProperties

    @Autowired
    lateinit var netatmoProperties: NetatmoProperties

    @Autowired
    lateinit var commPort : CommPort

    @Autowired
    lateinit var crcCalc : CrcCalculator
    
    @Autowired
    lateinit var influxDB : InfluxDB

    lateinit var `in` : InputStream
    lateinit var `out` : OutputStream
    lateinit var heizkreisTag : String

    private var ticketStartTS:LocalDateTime? = null
    private var last9205TS:LocalDateTime? = null
    private var last77070xTS:LocalDateTime? = null
    private var last03050xTS:LocalDateTime? = null

    @PostConstruct
    fun init() {
        `in` = commPort.inputStream
        `out` = commPort.outputStream
        heizkreisTag = properties.heizkreis.tagName

        if (properties.korrekturfaktor == null) {
            properties.korrekturfaktor = BigDecimal("1.4")
        }

        if (properties.korrekturoffset == null) {
            properties.korrekturoffset = BigDecimal("-7.4")
        }
    }

    @PreDestroy
    fun close() {
        log.info("Cleaning up...")
        try { commPort.close() } catch (e : Exception) {}
        try { influxDB.close() } catch (e : Exception) {}
    }

    @Async
    open fun process() {
        log.info("###Start Processing with Thread name: " + currentThread().name)

        GlobalScope.newCoroutineContext(CoroutineExceptionHandler(fun (ctx, e:Throwable) {
            log.error("Error in coroutine", e)
        }))

        val buffer = ByteArray(1024)

        var sum = 0
        try {
            while (`in`.available() > 0) {
                sum += `in`.read(buffer)
            }
        } catch (ignore: Exception) { }

        log.info("Skipped $sum bytes from serial buffer before start handling tickets")

        val ticket = ByteArray(128)
        var ticketpos = 0
        val instreamlist = LinkedList<Byte>()
        var end = false
        var start = true
        val ticketbytes = ArrayList<Byte>(128)
        try {
            var len = `in`.read(buffer)
            do {
                for (i in 0 until len) {
                    instreamlist.add(buffer[i])
                }

                while (!instreamlist.isEmpty()) {
                    val aByte = instreamlist.removeFirst()

                    if (ticketStartTS == null) {
                        ticketStartTS = now()
                    }

                    try {
                        ticket[ticketpos] = aByte!!
                        ticketpos++
                    } catch (e: ArrayIndexOutOfBoundsException) {
                        print(" Ticket too long? ")
                    }

                    when {
                        start && aByte == 0x10.toByte() -> {
                            // don't print start bytes
                        }
                        start && aByte == 0x02.toByte() -> // don't print start bytes
                            start = false
                        !end && aByte == 0x10.toByte() -> // don't print end bytes
                            end = true
                        end && aByte == 0x10.toByte() -> {
                            ticketbytes.add(aByte) // it was an escaped "value byte"
                            end = false // 0x10 is escaped by sending it twice in a row
                            ticketpos-- // ignore the second 0x10
                        }
                        end && aByte == 0x03.toByte() -> {
                            // don't print end bytes
                            storeValues(ticketStartTS, ticketbytes.toByteArray())
                            ticketpos = 0
                            println()
                            end = false
                            start = true
                            ticketStartTS = null
                            ticketbytes.clear()
                        }
                        else -> {
                            ticketbytes.add(aByte)
                            end = false
                        }
                    }
                }

                len = this.`in`.read(buffer)
            } while (len > -1)
        } catch (e: IOException) {
            log.error("Error in main read loop", e)
        }
    }

    private fun storeValues(ticketStartTS : LocalDateTime?, ticket: ByteArray) {
        print(rightPad(ticketStartTS?.format(TIME_FORMATTER), 15, '0') + ":")
        print(leftPad(MILLIS.between(ticketStartTS, now()).toString(), 4, ' ') + "ms")
        print(checkChecksum(ticket))
        when {
            ticket ist ACK_BD_MD -> {
                val delta = if (last9205TS == null) 0 else MILLIS.between(last9205TS, ticketStartTS)
                print(" ACK_BD_MD  ${delta}ms")
                last9205TS = null
            }

            ticket ist TEMP_BD_MD -> {
                print(" TEMP_BD_MD")
                val modus = byte2short(ticket, TEMP_BD_MD.size)
                print(getModusString(modus))
                val innenIst = convertTemp(ticket, TEMP_BD_MD.size + 2)
                print(" Innen ist: " + getTemp(innenIst))
                val innenSoll = convertTemp(ticket, TEMP_BD_MD.size + 4)
                print(" Innen soll: " + getTemp(innenSoll))
                val vorlaufZiel = convertTemp(ticket, TEMP_BD_MD.size + 6)
                print(" Vorlauf Ziel: " + getTemp(vorlaufZiel))
                val wasserSoll = convertTemp(ticket, TEMP_BD_MD.size + 8)
                print(" Wasser soll: " + getTemp(wasserSoll))

                GlobalScope.launch {
                    updateProperties(Betriebsart.fromInt(modus), innenIst, innenSoll, wasserSoll, vorlaufZiel)
                }

                GlobalScope.launch {
                    val pointBuilder = getInfluxPoint()
                            .addField("innenIst", innenIst)
                            .addField("innenSoll", innenSoll)
                            .addField("wasserSoll", wasserSoll)

                    if (vorlaufZiel != null) {
                        pointBuilder.addField("vorlaufZiel", vorlaufZiel)
                    }

                    influxDB.write(pointBuilder.build())
                }
            }

            ticket ist TEMP_IST -> {
                GlobalScope.launch  {
                    answer(TEMP_IST)
                }

                print(" TEMP_IST  ")
                last9205TS = ticketStartTS
                val aussentemp = convertTemp(ticket, TEMP_IST.size)
                print(" Aussen: " + getTemp(aussentemp))
                val ruecklaufIst = convertTemp(ticket, TEMP_IST.size + 2)
                print(" Rücklauf ist: " + getTemp(ruecklaufIst))
                val vorlaufIst = convertTemp(ticket, TEMP_IST.size + 4)
                print(" Vorlauf ist: " + getTemp(vorlaufIst))
                val wasserIst = convertTemp(ticket, TEMP_IST.size + 6)
                print(" Wasser ist: " + getTemp(wasserIst))
                val sol = convertTemp(ticket, TEMP_IST.size + 8)
                print(" Sol: " + getTemp(sol))

                GlobalScope.launch {
                    influxDB.write(getInfluxPoint()
                            .addField("aussen", aussentemp)
                            .addField("ruecklaufIst", ruecklaufIst)
                            .addField("vorlaufIst", vorlaufIst)
                            .addField("wasserIst", wasserIst)
                            .addField("sol", sol)
                            .build()
                    )
                }

                GlobalScope.launch {
                    properties.aussentemp = aussentemp
                }
            }

            ticket ist TEMP_SOLL -> {
                GlobalScope.launch  {
                    answer(TEMP_SOLL)
                }

                print(" TEMP_SOLL ")
                last9205TS = ticketStartTS
                val kesselSoll = convertTemp(ticket, TEMP_SOLL.size)
                print(" Kessel soll: " + getTemp(kesselSoll))
                print(" y: " + getTemp(ticket, TEMP_SOLL.size + 2))
                val vorlaufSoll = convertTemp(ticket, TEMP_SOLL.size + 4)
                print(" Vorlauf soll: " + getTemp(vorlaufSoll))
                print(" w: " + getTemp(ticket, TEMP_SOLL.size + 6))
                print(" Wasser soll: " + getTemp(ticket, TEMP_SOLL.size + 8))
                print(" v: " + getTemp(ticket, TEMP_SOLL.size + 10))

                GlobalScope.launch {
                    influxDB.write(getInfluxPoint()
                            .addField("ruecklaufSoll", kesselSoll) // TODO rename database field
                            .addField("vorlaufSoll", vorlaufSoll)
                            .build()
                    )
                }
            }

            ticket ist UML_PARAMS -> {
                GlobalScope.launch  {
                    answer(UML_PARAMS)
                }

                // Usually this ticket contains: a: 35.7C b: 25.4C c: 116.8C d: 0.0C e: 0.0C f: 0.0C
                //                           or: a: 36.1C b: 23.5C c: 119.2C d: 0.0C e: 0.0C f: 0.0C
                // but if temperatures are different from these, then display them here
                print(" UML_PARAMS")
                last9205TS = ticketStartTS
                val fusspunkt = convertTemp(ticket, UML_PARAMS.size) // Fusspunkt in °C  --> Messwert ist eingestellter Wert
                print(" FussP: " + getTemp(fusspunkt))
                val steigung = convertParam(ticket, UML_PARAMS.size + 2)  // Steigung: 0: 0.3; 0.4: 5.4; 0.8: 15.2; 1.2: 25.4; 1.6: 31.4; 2.0: 41.7; 2.4: 44.8  --> Messwert ist eingestellter Wert mal etwa 20
                print(" Steig: " + getTemp(steigung, ""))
                val tempCompensation = convertParam(ticket, UML_PARAMS.size + 4)  // Innentemp Comp. K/K Min (0): 2.3°C Max (10): 199.2°C  --> Messwert ist eingestellter Wert mal etwa 20
                print(" Comp: " + getTemp(tempCompensation, "K/K"))
                print(" d: " + getTemp(ticket, UML_PARAMS.size + 6))
                print(" e: " + getTemp(ticket, UML_PARAMS.size + 8))
                print(" f: " + getTemp(ticket, UML_PARAMS.size + 10))

                GlobalScope.launch {
                    properties.fusspunkt = fusspunkt
                    properties.steigung = steigung
                    properties.temp_compensation = tempCompensation
                }
            }

            ticket ist TEMP_SOLAR -> {
                GlobalScope.launch  {
                    answer(TEMP_SOLAR)
                }

                print(" TEMP_SOLAR")
                last9205TS = ticketStartTS
                print(" g: " + getTemp(ticket, TEMP_SOLAR.size))
                print(" h: " + getTemp(ticket, TEMP_SOLAR.size + 2))
                print(" i: " + getTemp(ticket, TEMP_SOLAR.size + 4))
                val energieWoche = ticket[TEMP_SOLAR.size + 7].toInt() and 0x00FF
                print(" EWochTot: " + energieWoche + "kWh")
                val energieTag = byte2short(ticket, TEMP_SOLAR.size + 8) // eventuell ist das auch die totale heizleistung (inkl. strom) pro tag. Es gibt tage, da steigt dies auch während der nacht an (zumindest für modul 0)
                print(" Etot: " + energieTag + "kWh")

                GlobalScope.launch {
                    influxDB.write(getInfluxPoint()
                            .addField("woch_kWh", energieWoche)
                            .addField("sol_kWh", energieTag)
                            .build()
                    )
                }
            }

            ticket ist BRENNER -> {
                print(" BRENNER   ")
                val sollKessel = ticket[BRENNER.size].toInt() and 0x00FF
                print(" Soll Brenner: ${sollKessel}C")
                val brenner_status = ticket[BRENNER.size + 3] == 0x01.toByte()
                print(" Brenner ${if (brenner_status) "ein" else "aus"}")

                GlobalScope.launch {
                    if (brenner_status != properties.brenner_status) {
                        properties.brenner_status = brenner_status
                        influxDB.write(getInfluxPoint()
                                .addField("brenner_status", brenner_status)
                                .build()
                        )
                    }
                }
            }

            ticket ist PUMPEN -> {
                GlobalScope.launch  {
                    answer(PUMPEN)
                }

                // Umwälzumpe
                // 08 Standard /standby?
                // 18 Wahrscheinlich Boilerladepumpe, 12:00 mittags
                // 88 Umwälzpumpe ein
                // 0C ???? meist, wenn Wasserheizung prinzipiell eingeschaltet, aber aktuell grad nicht läuft?
                // modul-0_2018-07-17.log:21.23:43:43.060: 0C Umwälzpumpe: aus,  Eine Differenz: 0 / 00
                // Die "Differenz" steuert das Mischventil: kleine Werte: kurze Ansteuerung, grosse Werte: lange Ansteuerung. Positiv: öffnen. Negativ: schliessen; wahrscheinlich Prozentwert

                // Normalwert (Zeit), alles aus: 01 01 08 00 FF FF
                // Manueller Modus am UML:       01 06 98 00 FF FF
                // Schalter auf aus am UML:      01 06 08 9C FF FF
                // Schalter auf 1 am UML:        01 06 98 64 FF FF (alles ein)
                // Schalter auf 2 am UML:        01 06 08 9C FF FF (alles aus)



                print(" PUMPEN    ")
                last9205TS = ticketStartTS
                val thirdbyte = ticket[PUMPEN.size + 2]
                val umwaelzpumpeStatus = (thirdbyte.toInt() and 0x0080) != 0
                val boilerpumpeStatus = (thirdbyte.toInt() and 0x0010) != 0
                val mischventil = ticket[PUMPEN.size + 3].toInt()

                printhex(ticket[PUMPEN.size])
                printhex(ticket[PUMPEN.size + 1])
                printhex(ticket[PUMPEN.size + 2])
                printhex(ticket[PUMPEN.size + 3])
                printhex(ticket[PUMPEN.size + 4])
                printhex(ticket[PUMPEN.size + 5])
                print(" Umwälzpumpe: ${if (umwaelzpumpeStatus) "ein" else "aus"},${if (boilerpumpeStatus) " Boilerpumpe?," else ""} Mischventil: $mischventil")

                GlobalScope.launch {
                    val pointBuilder = getInfluxPoint()
                            .addField("mischventil", mischventil)

                    if (umwaelzpumpeStatus != properties.umwaelzpumpe_status) {
                        properties.umwaelzpumpe_status = umwaelzpumpeStatus
                        pointBuilder.addField("umwaelzpumpe_status", umwaelzpumpeStatus)
                    }

                    if (boilerpumpeStatus != properties.boilerpumpe_status) {
                        properties.boilerpumpe_status = boilerpumpeStatus
                        pointBuilder.addField("boilerpumpe_status", boilerpumpeStatus)
                    }

                    influxDB.write(pointBuilder.build())
                }
            }

            ticket ist ASK_RAUM -> {
                print(" ASK_RAUM   Bitte Raumtemp & Betriebsmodi")
                GlobalScope.launch  {
                    answer(ASK_RAUM)
                }
            }

            ticket ist _770704 -> {
                print(" _770704   ")
                val delta = if (last77070xTS == null) 0 else MILLIS.between(last77070xTS, ticketStartTS)
                last77070xTS = ticketStartTS
                print(" TICK 77 07 04 ${delta}ms")
            }

            ticket ist _77070E -> {
                print(" _77070E   ")
                val delta = if (last77070xTS == null) 0 else MILLIS.between(last77070xTS, ticketStartTS)
                last77070xTS = ticketStartTS
                print(" TACK 77 07 0E ${delta}ms")
            }

            ticket ist _030504 -> {
                print(" _030504   ")
                val delta = if (last03050xTS == null) 0 else MILLIS.between(last03050xTS, ticketStartTS)
                last03050xTS = ticketStartTS
                print(" tick 03 05 04 ${delta}ms")
            }

            ticket ist _03050E -> {
                print(" _03050E   ")
                val delta = if (last03050xTS == null) 0 else MILLIS.between(last03050xTS, ticketStartTS)
                last03050xTS = ticketStartTS
                print(" tack 03 05 0E ${delta}ms")
            }

            ticket ist KESSEL -> {
                print(" _67080305 ")
                val kesselsoll = ticket[KESSEL.size].toInt() and 0x00FF
                print(" Kessel soll 67080305: $kesselsoll")

                GlobalScope.launch {
                    influxDB.write(getInfluxPoint()
                            .addField("kesselsoll", kesselsoll) // ehemals somediff2
                            .build()
                    )
                }
            }

            ticket ist ASK_TIME -> {
                print(" ASK_TIME   Bitte Zeit mitteilen")
                GlobalScope.launch  {
                    answer(ASK_TIME)
                }
            }

            ticket ist TIME_BD_MD -> {
                print(" TIME_BD_MD Neue Zeit: ${ticket[TIME_BD_MD.size + 4]}:${ticket[TIME_BD_MD.size + 5]}:${ticket[TIME_BD_MD.size + 6]}, ")

                when (ticket[TIME_BD_MD.size + 7].toInt()) {
                    0 -> print("Sonntag")
                    1 -> print("Montag")
                    2 -> print("Dienstag")
                    3 -> print("Mittwoch")
                    4 -> print("Donnerstag")
                    5 -> print("Freitag")
                    6 -> print("Samstag")
                    else -> print("Unbekannter Wochentag")
                }
            }

            else -> {
                print(" UNKNOWN   ")
                printTicketBytes(ticket)
            }
        }
    }

    private fun getInfluxPoint(): Point.Builder {
        return Point.measurement("temp")
                .time(System.currentTimeMillis(), MILLISECONDS)
                .tag("heizkreis", heizkreisTag)
    }

    private fun updateProperties(modus: Betriebsart, innenIst: BigDecimal?, innenSoll: BigDecimal?, wasserSoll: BigDecimal?, vorlaufZiel: BigDecimal?) {
        try {
            // Load properties to catch manual updates to the file
            properties.init()

            if (properties.master == BEDIENMODUL) {
                properties.betriebsart = modus
                properties.innentemp_ist = innenIst
                properties.innentemp_soll = innenSoll
                properties.wasser_soll = wasserSoll
                properties.vorlauf_ziel = vorlaufZiel
            }

        } catch (e: Exception) {
            log.error("updateProperties(): $e")
        }
    }

    suspend fun answer(ticket: ByteArray) {
        delay(80)
        when {
            ticket ist TEMP_IST ||
            ticket ist TEMP_SOLL ||
            ticket ist UML_PARAMS ||
            ticket ist PUMPEN ||
            ticket ist TEMP_SOLAR
            -> {
                // BRENNER, _67080305, _770704, _77070E, _030504, _03050E werden nicht ACKed
                writeTicket(`out`, ACK_BD_MD)
            }

            ticket ist ASK_RAUM -> {
                writeTicket(`out`, createRaumtempTicket())
            }

            ticket ist ASK_TIME -> {
                writeTicket(`out`, createCurrentTimeTicket(now()))
                properties.setze_zeit = false
            }
        }
    }

    private fun writeTicket(out: OutputStream, ticketbytes: ByteArray) {
        if (properties.master != RASPI) {
            return
        }

        val ticket = LinkedList(ticketbytes.asList()).escapeAndFrame(crcCalc).toByteArray()
//        val ts = now()
        out.write(ticket)
        out.flush()

// uncomment if logging is needed
//        print(rightPad(ts.format(TIME_FORMATTER), 15, '0') + ":")
//        print(leftPad(MILLIS.between(ts, now()).toString(), 4, ' ') + "ms Wrote: ")
//        printTicketBytes(ticket)
//        println()
    }

    private fun checkChecksum(ticket: ByteArray): String {
        if (ticket.size > 1) {
            val checksum = crcCalc.calc(ticket, 0, ticket.size - 1 /* 1 byte checksum*/)

            return if (checksum.toByte() == ticket[ticket.size - 1]) "" else " NOK! [${toHexString(checksum)}]"
        } else {
            return " [Too short]"
        }
    }

    private fun getParam(data: ByteArray, index: Int, postfix: String = "") = (convertParam(data, index)?.toString() ?: "--") + postfix
    private fun getTemp(data: ByteArray, index: Int, postfix: String = "C") = (convertTemp(data, index)?.toString() ?: "--") + postfix
    private fun getTemp(temp: BigDecimal?, postfix: String = "C") = (temp?.toString() ?: "--") + postfix

    private fun getModusString(modus: Int): String {
        // Okt 06. 22:03:49.031: <Unknown Mode> (0801) Innen ist: 21.6C Innen soll: 5.0C Kessel Ziel?: 0.0C Wasser soll Tag: 50.0C
        // Okt 06. 22:03:49.574: 0C Umwälzpumpe: aus,  Eine Differenz: 0 / 00
        // -> Unknown Mode> (0801); normalerweise ist Umwälzpumpe 0x08

        // Unten: Nach dem Aufstecken 5404 (im Party-Mode) und 1401 (Automatik Nacht)
        // Unten: 0x5001 wenn Aut. Tag wäre und man auf Nacht schaltet (aber immer noch im Aut-Mode) (normal ist D001) 0101 statt normal 1101
        // Oben:  0x4001 wenn Aut. Tag wäre und man auf Nacht schaltet (aber immer noch im Aut-Mode) (normal ist C001) 0100 statt normal 1100
        // Oben:  0x8001 wenn Aut. Nacht wäre und man auf Tag schaltet (aber immer noch im Aut-Mode) (normal ist 0001) 1000 statt normal 0000

        // Okt 28.18:20:24.085
        // Oben: Nach Aufstecken erst 1006 dann D401 dann D001 (dauernd -> Automatik Nacht + neue Zeit)

        // LSB
        // 0x01: Automatik
        // 0x02: Fix Tag
        // 0x03: Fix Nacht
        // 0x04: Party
        // 0x05: Handbetrieb (Annahme)
        // 0x06: Heizung aus

        // MSB
        // unten     oben
        // 1101      1x00  Tagbetrieb
        // 0001/1100 0000  Nachtbetrieb
        // 0101      0000  Party
        // 0001      0000  Heizung aus
        // xxx1      xxx1  Neue Zeit verfügbar
        //           00001000 ???? (0801)
        // --> Die Unterschiede zwischen oben und unten kommen höchstwahrscheinlich daher, weil das untere Modul seit ewig darauf wartet, dass es nach der Zeit gefragt wird....
        when (modus and 0xFFFF) {
        //  unten         oben
        //  1101
            0xD001,       0xC001 -> return " <Aut. Tag> (${shortToHex(modus)})" // oben: Aut. Tag D001 zeigt den modus "Zeit setzen" an
        //  0001
            0x1001,       0x0001 -> return " <Aut. Nacht> (${shortToHex(modus)})" // 0x1001 noch nicht verifiziert (ist auch Abwesenheitsmodus)
        //  1101
            0xD002,       0x8002, 0xC002 -> return " <Fix Tag> (${shortToHex(modus)})" // unten und oben C002 ist auch fix tag 0x9002 zeigt den modus "Zeit setzen" an
        //  0001
            0x1003,       0x0003 -> return " <Fix Nacht> (${shortToHex(modus)})" // 0x1003 noch nicht verifiziert
        //  0101
            0x5004,       0x0004, 0x4004 -> return " <Party> (${shortToHex(modus)})" // 0x4004 oben, im Sommer wenn wasser-soll auf 45°C
        //  0001
            0x1006,       0x0006 -> return " <Heizung aus> (${shortToHex(modus)})" // 0x1006 noch nicht verifiziert
            else -> return " <Unknown Mode> (${shortToHex(modus)})"
        }
    }

    // TODO test
    fun createRaumtempTicket() : ByteArray {
        val list = LinkedList(TEMP_BD_MD.asList())

        var betriebsartHex = calcBetriebsart()

        list.addAll(short2byte(betriebsartHex))
        list.addAll(temp2byte(getInnenTemp()))
        list.addAll(temp2byte(properties.innentemp_soll))
        list.addAll(temp2byte(calcVorlaufTemp()))
        list.addAll(temp2byte(properties.wasser_soll))
        return list.toByteArray()
    }

    private fun calcBetriebsart(): Int {
        val betriebsart = properties.betriebsart
        var betriebsartHex = betriebsart.hex

        if (properties.setze_zeit) {
            betriebsartHex = betriebsartHex or Betriebsart.NEUE_ZEIT_VERFUEGBAR
        }

        if (betriebsart == FIX_TAG ||
            (betriebsart == AUTOMATIK && isAwakeTime())
        ) {
            betriebsartHex = betriebsartHex or Betriebsart.AUT_TAG_ZUSATZ
        }

        return betriebsartHex
    }

    private fun isAwakeTime(): Boolean {
        val hourOfDay = now().hour

        return hourOfDay >= 6 // hourOfDay is null-based
    }

    private fun getInnenTemp(): BigDecimal? {
        if (properties.fake_innen_temp != null) {
            // for testing only, of course
            return properties.fake_innen_temp
        } else {
            netatmoProperties.init() // actually, refresh
            return when {
                // in case the real temperature is not available, return 5°C to avoid the heating panicking
                properties.heizkreis == OBEN -> netatmoProperties.temp_oben ?: 5.toBigDecimal()
                properties.heizkreis == UNTEN -> netatmoProperties.temp_unten ?: 5.toBigDecimal()
                else -> null
            }
        }
    }

    /**
     * (Tsoll-Tist)*Tcomp+(Tsoll-Tauss-5)*Steigung*KorrekturFaktor+Fusspunkt+KorrekturOffset
     *
     * Excel: =(I22-J$7)*J$2+(I22-J$6-5)*J$3*J$1+J$4+J$8
     */
    private fun calcVorlaufTemp(): BigDecimal? {
        val Tsoll           = properties.innentemp_soll ?: BigDecimal(5)
        val Tist            = getInnenTemp() ?: BigDecimal(5)
        val Tcomp           = properties.temp_compensation ?: BigDecimal.ZERO
        val Tauss           = properties.aussentemp ?: BigDecimal(10)
        val Steigung        = properties.steigung ?: BigDecimal("1.2")
        val Fusspunkt       = properties.fusspunkt ?: BigDecimal(35)
        val KorrekturFaktor = properties.korrekturfaktor ?: BigDecimal("1.4")
        val KorrekturOffset = properties.korrekturoffset ?: BigDecimal("-7.4")

// uncomment if logging is needed
//        val ts = now()
//        print(rightPad(ts.format(TIME_FORMATTER), 15, '0') + ":")
//        println("                  Tsoll: $Tsoll Tist: $Tist Tcomp: $Tcomp Tauss: $Tauss Steig: $Steigung FussP: $Fusspunkt KorFakt: $KorrekturFaktor KorOffs: $KorrekturOffset")

        return calcVorlaufTemp0(Tsoll, Tist, Tauss, Steigung, Fusspunkt, Tcomp, KorrekturFaktor, KorrekturOffset)
    }


    companion object {

        // 10 im Datenstrom wird escaped dadurch dass es 2 mal hintereinander gesendet wird

        // 7F: Address-Id Bedienmodul
        // 00 oder 91/92: Address-Id Controller BUL/WVF (brenner, mischer, ...)
        // 05 oder 91/92: Address-Id Controller UML? (heizkreis,sollwerte)

        // Könnte auch ein Temp-Sollwert sein: 49-71°C
        // 10 02 92 00 7F 03 02 67 08 03 05
        // 10 02 92 00 7F 03 02 67 08 02 04 46 die 46 am schluss wechselt manchmal auch auf 47,45,41,40,31,3F,3E,3D. Und manchmal auch 00. Dann ist auch das letzte datenbyte 00 statt 01

        // Sent from Bedienmodul
        val ACK_BD_MD  = byteArrayOf(0x9B.toByte(), 0x7F, 0x05, 0x02, 0x83.toByte(), 0xE7.toByte(), 0x00) // complete ticket, without checksum (0x5C)
        val TEMP_BD_MD = byteArrayOf(0x9B.toByte(), 0x7F, 0x05, 0x02, 0x83.toByte(), 0xF7.toByte(), 0x00, 0x06, 0x21, 0x0A) // Antwort auf Kommando 0x21
        val TIME_BD_MD = byteArrayOf(0x9B.toByte(), 0x7F, 0x05, 0x02, 0x83.toByte(), 0xF7.toByte(), 0x00, 0x06, 0x25, 0x08 /*00 00 00 00 0A 2F 3B 00 7F*/)  // Antwort auf Kommando 0x25

        // Sent from controller
        // Das zweite Byte 0x05 heisst vielleicht "erbitte Antwort", denn nur diese Tickets werden beantwortet
        // Quittiert nach etwa 200ms, 90ms, 170ms
        val PUMPEN     = byteArrayOf(0x92.toByte(), 0x05, 0x7F, 0x03, 0x02, 0x67, 0x08, 0x20, 0x06) // scheint vom UML zu kommen
        // Antwort nach 140ms, 210ms, 60ms
        val ASK_RAUM   = byteArrayOf(0x92.toByte(), 0x05, 0x7F, 0x03, 0x02, 0x77, 0x07, 0x21, 0x00, 0x05)  // complete ticket - Ask Raumtemp & Betriebsmodus -> dieses Ticket kommt immer genau bevor die Raumtemp kommt)
        // Quittiert nach 120ms, 90ms, 80ms
        val TEMP_IST   = byteArrayOf(0x92.toByte(), 0x05, 0x7F, 0x03, 0x02, 0x67, 0x08, 0x22, 0x0A)
        // Quittiert nach 90ms
        val TEMP_SOLAR = byteArrayOf(0x92.toByte(), 0x05, 0x7F, 0x03, 0x02, 0x67, 0x08, 0x23, 0x0A)
        // Quittiert nach 90ms
        val TEMP_SOLL  = byteArrayOf(0x92.toByte(), 0x05, 0x7F, 0x03, 0x02, 0x67, 0x08, 0x24, 0x0C)
        val ASK_TIME   = byteArrayOf(0x92.toByte(), 0x05, 0x7F, 0x03, 0x02, 0x77, 0x07, 0x25, 0x00, 0xA8.toByte()) // complete ticket
        val UML_PARAMS = byteArrayOf(0x92.toByte(), 0x05, 0x7F, 0x03, 0x02, 0x67, 0x08, 0x26, 0x0C) // kommen vom UML C1
// diese beiden sind UML_PARAMS-tickets mit speziellen Parameter-Settings (Fusspunkt, Steigung, etc)
//        val TEMP_4a    = byteArrayOf(0x92.toByte(), 0x05, 0x7F, 0x03, 0x02, 0x67, 0x08, 0x26, 0x0C, 0x0D, 0xFA.toByte(), 0x09, 0xED.toByte(), 0x2D, 0xA6.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x46)
//        val TEMP_4b    = byteArrayOf(0x92.toByte(), 0x05, 0x7F, 0x03, 0x02, 0x67, 0x08, 0x26, 0x0C, 0x0E, 0x21, 0x09, 0x30, 0x2E, 0x91.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xC0.toByte())
        // Not answered tickets
        val BRENNER    = byteArrayOf(0x92.toByte(), 0x00, 0x7F, 0x03, 0x02, 0x67, 0x08, 0x02, 0x04) // brennersteuerung
        val KESSEL     = byteArrayOf(0x92.toByte(), 0x00, 0x7F, 0x03, 0x02, 0x67, 0x08, 0x03, 0x05)
        val _770704    = byteArrayOf(0x92.toByte(), 0x00, 0x7F, 0x03, 0x02, 0x77, 0x07, 0x04, 0x00, 0x54.toByte()) // complete ticket TICK
        val _77070E    = byteArrayOf(0x92.toByte(), 0x00, 0x7F, 0x03, 0x02, 0x77, 0x07, 0x0E, 0x00, 0xA4.toByte()) // complete ticket TACK
        val _030504    = byteArrayOf(0x91.toByte(), 0x00, 0x7F, 0x03, 0x02, 0x03, 0x05, 0x04, 0x00, 0x12)          // complete ticket tick
        val _03050E    = byteArrayOf(0x91.toByte(), 0x00, 0x7F, 0x03, 0x02, 0x03, 0x05, 0x0E, 0x00, 0xE2.toByte()) // complete ticket tack


        // Zeit verstellen:
        // Bedienmodul meldet sich neu an (offenbar) mit FF FF (nicht immer)
        // Dann kommt irgendwann mal Bedienmodus 9002 (bei fix Tag -> C002) (nicht immer)
        // Heizung antwortet mit 92 05 7F 03 02 77 07 25 00 A8 (erfrage Zeit)
        // Bedienmodul dann mit 9B 7F 05 02 83 F7 00 06 25 08 00 00 00 00 17 19 06 06 AF
        // wobei 17: Stunde (23), 19: Minute (25), 06: Sekunde, 06: Wochentag (Samstag)

        // Nach dem wiederaufstecken des entfernten Moduls: Betriebsmodus D402 (bei fix Tag)

        fun createCurrentTimeTicket(now: LocalDateTime) : ByteArray {
            with(LinkedList(TIME_BD_MD.asList())) {
                add(0x00)
                add(0x00)
                add(0x00)
                add(0x00)
                add(now.hour.toByte())
                add(now.minute.toByte())
                add(now.second.toByte())
                add((now.get(DAY_OF_WEEK) % 7).toByte()) // Sunday is 0, other days are the same as for Java
                return toByteArray()
            }
        }

        fun calcVorlaufTemp0(
                Tsoll      : BigDecimal,
                Tist       : BigDecimal,
                Tauss      : BigDecimal,
                Steigung   : BigDecimal,
                Fusspunkt  : BigDecimal,
                Tcomp      : BigDecimal,
                KorrFaktor : BigDecimal,
                KorrOffset : BigDecimal
        ) : BigDecimal {
            val result = (Tsoll - Tist) * Tcomp + (Tsoll - Tauss - FIVE) * Steigung * KorrFaktor + Fusspunkt + KorrOffset
            return result.min(SEVENTY).max(BigDecimal.ZERO).setScale(1, RoundingMode.HALF_DOWN)
        }
    }
}

