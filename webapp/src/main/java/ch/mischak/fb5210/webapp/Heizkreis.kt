/*
 * MIT License
 *
 * Copyright (c) 2023 Mischa Kölliker
 */
package ch.mischak.fb5210.webapp

enum class Heizkreis {
    OBEN, // ordinal 0
    UNTEN; // ordinal 1

    val tagName = name.toLowerCase()
    val channelNumber = ordinal

    companion object {
        fun fromSystemProperty() : Heizkreis {
            return fromDevice(System.getProperty("serial.device") ?: "")
        }

        fun fromDevice(deviceName: String): Heizkreis {
            return when {
                deviceName.endsWith('0') -> OBEN
                deviceName.endsWith('1') -> UNTEN
                else                     -> UNTEN
            }
        }

        fun fromString(name: String?): Heizkreis {
            return when(name) {
                OBEN.name  -> OBEN
                UNTEN.name -> UNTEN
                else       -> fromSystemProperty()
            }
        }
    }
}
