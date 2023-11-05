/*
 * MIT License
 *
 * Copyright (c) 2023 Mischa Kölliker
 */
package ch.mischak.fb5210.webapp

import ch.mischak.fb5210.crc.CrcCalculator
import com.google.common.primitives.Bytes
import org.json.JSONArray
import org.json.JSONObject
import org.springframework.beans.factory.annotation.Qualifier
import java.math.BigDecimal
import java.math.BigDecimal.ZERO
import java.util.*
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.ANNOTATION_CLASS
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FIELD
import kotlin.annotation.AnnotationTarget.FILE
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlin.annotation.AnnotationTarget.PROPERTY_SETTER
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

fun LinkedList<Byte>.escapeAndFrame(crcCalc : CrcCalculator): LinkedList<Byte> {
    // checksum is without frame bytes and before escaping 0x10's
    val checksum = crcCalc.calc(this.toByteArray(), 0, this.size)

    var i = 0
    while (i < this.size) {
        if (this[i] == 0x10.toByte()) {
            this.add(i, 0x10.toByte())
            i++
        }

        i++
    }

    this.add(0, 0x02.toByte())
    this.add(0, 0x10.toByte())

    this.add(checksum.toByte())

    this.add(0x10)
    this.add(0x03)

    return this
}

fun printTicketBytes(ticketbytes: ByteArray) {
    for (ticketbyte in ticketbytes) {
        printhex(ticketbyte)
    }
}

private val hexArray = "0123456789ABCDEF".toCharArray()

fun printhex(aByte: Byte) {
    print(" " + hexArray[(aByte.toInt() and 0x00FF) ushr 4] + hexArray[aByte.toInt() and 0x000F])
}

fun shortToHex(theShort: Int) = shortToHex("", theShort)

fun shortToHex(prefix: String, theShort: Int) =
    prefix + hexArray[theShort.ushr(12) and 0x0F] + hexArray[theShort.ushr(8) and 0x0F] + hexArray[theShort.ushr(4) and 0x0F] + hexArray[theShort and 0x0F]


fun bytesToHex(bytes: ByteArray, len: Int): String {
    val hexChars = CharArray(len * 2)
    for (j in 0 until len) {
        val v = bytes[j].toInt() and 0x00FF
        hexChars[j * 2] = hexArray[v.ushr(4)]
        hexChars[j * 2 + 1] = hexArray[v and 0x0F]
    }
    return String(hexChars)
}

infix fun ByteArray.ist(template: ByteArray) : Boolean {
    return Bytes.indexOf(this, template) == 0
}

fun byte2short(data: ByteArray, index: Int): Int {
    return data[index].toInt() shl 8 or (data[index + 1].toInt() and 0x00FF)
}

fun short2byte(value : Int) : List<Byte> {
    return listOf((value shr 8 and 0x00FF).toByte(), (value and 0x00FF).toByte())
}

fun temp2byte(value : BigDecimal?): List<Byte> {
    return short2byte(((value ?: ZERO) * ONEHUNDRED).toInt())
}

fun convertTemp(data: ByteArray, index: Int) = if (data[index] == 0x7F.toByte() && data[index+1] == 0xFF.toByte()) null else BigDecimal.valueOf(byte2short(data, index).toLong()).divide(ONEHUNDRED, 1, BigDecimal.ROUND_DOWN)

fun convertParam(data: ByteArray, index: Int) = if (data[index] == 0x7F.toByte() && data[index+1] == 0xFF.toByte()) null else BigDecimal.valueOf(byte2short(data, index).toLong()).divide(TWOTHOUSAND, 2, BigDecimal.ROUND_DOWN)

val TWOTHOUSAND = BigDecimal(2000)
val ONEHUNDRED  = BigDecimal(100)
val FIVE        = BigDecimal(5)
val SEVENTY     = BigDecimal(70)
val THIRTY      = BigDecimal(30)


operator fun JSONArray.iterator() : Iterator<JSONObject>
        = (0 until length()).asSequence().map { get(it) as JSONObject }.iterator()

class InvalidTempSetting(s : String) : RuntimeException(s)


@Target(FIELD, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER, VALUE_PARAMETER, CLASS, FILE, ANNOTATION_CLASS)
@Retention(RUNTIME)
@MustBeDocumented
@Qualifier
annotation class HeizungUntenProperties
