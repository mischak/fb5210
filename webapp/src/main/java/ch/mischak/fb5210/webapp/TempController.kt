/*
 * MIT License
 *
 * Copyright (c) 2023 Mischa Kölliker
 */
package ch.mischak.fb5210.webapp

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal.ONE

@RestController
class TempController {

    @Autowired
    lateinit var properties : HeizungProperties

    @Autowired @HeizungUntenProperties
    lateinit var propertiesUnten : HeizungProperties


    @GetMapping("/temp")
    fun temp() : TempSetting {
        propertiesUnten.init() // this file need reloading, as it is usually never modified from here
        return TempSetting(
                properties.innentemp_soll,
                propertiesUnten.innentemp_soll,
                properties.betriebsart,
                propertiesUnten.betriebsart
        )
    }

    @PutMapping("/temp")
    fun temp(@RequestBody tempSetting : TempSetting) {
        if (tempSetting.sollOben != null) {
            if ((tempSetting.sollOben < ONE) or (tempSetting.sollOben > THIRTY)) {
                throw InvalidTempSetting("sollOben out of range: ${tempSetting.sollOben}")
            }

            properties.innentemp_soll = tempSetting.sollOben
        }

        if (tempSetting.sollUnten != null) {
            if ((tempSetting.sollUnten < ONE) or (tempSetting.sollUnten > THIRTY)) {
                throw InvalidTempSetting("sollUnten out of range: ${tempSetting.sollUnten}")
            }

            propertiesUnten.innentemp_soll = tempSetting.sollUnten
        }
    }

}
