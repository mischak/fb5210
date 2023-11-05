/*
 * MIT License
 *
 * Copyright (c) 2023 Mischa Kölliker
 */
package ch.mischak.fb5210.webapp

import ch.mischak.fb5210.webapp.Betriebsart.*
import org.junit.Assert.*
import org.junit.Test

class BetriebsartTest {

    @Test
    fun testModes() {
        assertEquals(AUTOMATIK,   Betriebsart.fromInt(0x0001))
        assertEquals(AUTOMATIK,   Betriebsart.fromInt(0xD001))
        assertEquals(FIX_TAG,   Betriebsart.fromInt(0x8002))
        assertEquals(FIX_TAG,   Betriebsart.fromInt(0xC002))
        assertEquals(FIX_TAG,   Betriebsart.fromInt(0xD002))
        assertEquals(FIX_NACHT, Betriebsart.fromInt(0x1003))
        assertEquals(FIX_NACHT, Betriebsart.fromInt(0x0003))
        assertEquals(UNKNOWN,   Betriebsart.fromInt(0x0000))
        assertEquals(UNKNOWN,   Betriebsart.fromInt(0xFFFF))
    }
}
