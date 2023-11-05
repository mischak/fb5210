/*
 * MIT License
 *
 * Copyright (c) 2023 Mischa Kölliker
 */
package ch.mischak.fb5210.webapp

import ch.mischak.fb5210.crc.AlgoParams
import ch.mischak.fb5210.crc.CrcCalculator
import junit.framework.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.util.*

internal class TelegramHandlerTest {

    private val crcCalculator = CrcCalculator(AlgoParams(8, 0xD5, 0x0, true, true, 0x0))

    @Test
    internal fun testEscapeAndFrameOk() {
        val list = LinkedList(listOf(0x9B.toByte(), 0x7F, 0x05, 0x02, 0x83.toByte(), 0xE7.toByte(), 0x00))

        val result = list.escapeAndFrame(crcCalculator)

        val expected = LinkedList(listOf(0x10, 0x02, 0x9B.toByte(), 0x7F, 0x05, 0x02, 0x83.toByte(), 0xE7.toByte(), 0x00, 0x5C, 0x10, 0x03))

        assertEquals(expected, result)
    }

    @Test
    internal fun testEscapeAndFrameEscapingOk() {
        val list = LinkedList(listOf(0x9B.toByte(), 0x7F, 0x05, 0x02, 0x10, 0x83.toByte(), 0xE7.toByte(), 0x00))

        val result = list.escapeAndFrame(crcCalculator)

        val expected = LinkedList(listOf(0x10, 0x02, 0x9B.toByte(), 0x7F, 0x05, 0x02, 0x10, 0x10, 0x83.toByte(), 0xE7.toByte(), 0x00, 0x34, 0x10, 0x03))

        assertEquals(expected, result)
    }

    @Test
    internal fun testCurrentTimeTicketSunday() {
        val result = TelegramHandler.createCurrentTimeTicket(LocalDateTime.parse("2018-10-14T22:41:30")).asList()

        val expected = listOf(0x9B.toByte(), 0x7F, 0x05, 0x02, 0x83.toByte(), 0xF7.toByte(), 0x00, 0x06, 0x25, 0x08, 0x00, 0x00, 0x00, 0x00, 0x16, 0x29, 0x1E, 0x00)

        assertEquals(expected, result)
    }

    @Test
    internal fun testCurrentTimeTicketMonday() {
        val result = TelegramHandler.createCurrentTimeTicket(LocalDateTime.parse("2018-10-15T00:01:03")).asList()

        val expected = listOf(0x9B.toByte(), 0x7F, 0x05, 0x02, 0x83.toByte(), 0xF7.toByte(), 0x00, 0x06, 0x25, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x03, 0x01)

        assertEquals(expected, result)
    }

    @Test
    internal fun testCalculateVorlauf1() {
        val result = TelegramHandler.calcVorlaufTemp0(
                21.toBigDecimal(),
                21.toBigDecimal(),
                11.toBigDecimal(),
                1.28.toBigDecimal(),
                35.toBigDecimal(),
                6.toBigDecimal(),
                1.4.toBigDecimal(),
                (-7.4).toBigDecimal()
        )

        assertEquals(36.6.toBigDecimal(), result)
    }

    @Test
    internal fun testCalculateVorlauf2() {
        val result = TelegramHandler.calcVorlaufTemp0(
                23.toBigDecimal(),
                16.3.toBigDecimal(),
                (-2).toBigDecimal(),
                1.28.toBigDecimal(),
                35.toBigDecimal(),
                6.toBigDecimal(),
                1.4.toBigDecimal(),
                0.toBigDecimal()
        )

        assertEquals(70.0.toBigDecimal(), result)
    }
}
