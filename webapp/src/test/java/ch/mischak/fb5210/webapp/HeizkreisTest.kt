/*
 * MIT License
 *
 * Copyright (c) 2023 Mischa Kölliker
 */
package ch.mischak.fb5210.webapp

import ch.mischak.fb5210.webapp.Heizkreis.OBEN
import ch.mischak.fb5210.webapp.Heizkreis.UNTEN
import org.junit.Test

import org.junit.Assert.*

class HeizkreisTest {

    @Test
    fun verifyOrdinalNumbers() {
        // As we trust on the ordinal number values, make sure they are as expected
        assertEquals(0, OBEN.channelNumber)
        assertEquals(1, UNTEN.channelNumber)
    }
}
