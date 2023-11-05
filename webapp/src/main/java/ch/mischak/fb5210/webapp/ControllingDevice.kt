/*
 * MIT License
 *
 * Copyright (c) 2023 Mischa Kölliker
 */
package ch.mischak.fb5210.webapp

enum class ControllingDevice {
    /**
     * Use this value, if the FB5210 is connected to the wire. Then this application is just listening to the traffic,
     * and is not answering any requests itself (-> read-only mode).
     */
    BEDIENMODUL,

    /**
     * Use this if this application is the only client connected to the wire. In this case, this application needs
     * to answer requests and set desired temperatures or operational states itself.
     */
    RASPI;

    companion object {
        fun fromString(name : String?) : ControllingDevice =
            when (name) {
                BEDIENMODUL.name -> BEDIENMODUL
                RASPI.name       -> RASPI
                else             -> BEDIENMODUL
            }
    }
}
