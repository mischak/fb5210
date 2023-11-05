/*
 * MIT License
 *
 * Copyright (c) 2023 Mischa Kölliker
 */
package ch.mischak.fb5210.webapp

enum class Betriebsart(val hex: Int) {

    AUTOMATIK  (0x0001),
    FIX_TAG    (0x0002),
    FIX_NACHT  (0x0003),
    PARTY      (0x0004),
    MANUAL     (0x0005), // Value is just an assumption!
    AUS        (0x0006),
    UNKNOWN    (0x0000);

    companion object {
        fun fromString(name : String?) : Betriebsart {
            return when(name) {
                AUTOMATIK.name -> AUTOMATIK
                FIX_TAG.name   -> FIX_TAG
                FIX_NACHT.name -> FIX_NACHT
                PARTY.name     -> PARTY
                MANUAL.name    -> MANUAL
                AUS.name       -> AUS
                UNKNOWN.name   -> UNKNOWN
                else           -> UNKNOWN
            }
        }

        fun fromInt(value : Int) : Betriebsart {
            return when {
                (value and 0x000F ) == AUTOMATIK.hex   -> AUTOMATIK
                (value and 0x000F ) == FIX_TAG.hex   -> FIX_TAG
                (value and 0x000F ) == FIX_NACHT.hex -> FIX_NACHT
                (value and 0x000F ) == PARTY.hex     -> PARTY
                (value and 0x000F ) == MANUAL.hex    -> MANUAL
                (value and 0x000F ) == AUS.hex       -> AUS
                else                                 -> UNKNOWN
            }
        }

        val NEUE_ZEIT_VERFUEGBAR     = 0x1000

        val AUT_TAG_ZUSATZ           = 0xC000   // Während Automatikbetrieb am Tag ist dieser Zusatz gesetzt
        val AUT_TAG_UEBERSTEUERUNG   = 0x8000   // Während Automatikbetrieb temporär Tag forcieren
        val AUT_NACHT_UEBERSTEUERUNG = 0x4000   // Während Automatikbetrieb temporär Nacht forcieren

        val NEUES_MODUL = 0x0400 // Annahme, nicht verifiziert

    }
}
