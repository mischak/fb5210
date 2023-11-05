/*
 * MIT License
 *
 * Copyright (c) 2023 Mischa Kölliker
 */
package ch.mischak.fb5210.webapp

import java.math.BigDecimal

data class TempSetting(val sollOben: BigDecimal?, val sollUnten: BigDecimal?, val modeOben: Betriebsart?, val modeUnten: Betriebsart?)
