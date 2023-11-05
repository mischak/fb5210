package ch.mischak.fb5210.simulator

import gnu.io.CommPortIdentifier
import gnu.io.SerialPort
import gnu.io.SerialPort.*
import java.io.BufferedReader
import java.io.File
import java.lang.Thread.sleep
import java.time.format.DateTimeFormatter.ISO_TIME
import java.time.temporal.ChronoField.MILLI_OF_DAY


var startTime: Long = -1
var lastTime: Long = -1

fun main(args : Array<String>) {
    val portName: String = if (args.size == 0) "/dev/ttyUSB0" else args[0]
    val reader: BufferedReader = File(if (args.size <= 1) "../logs/TwoWay-20180331.log" else args[1]).bufferedReader()

    print("Opening serial port device...")
    val portIdentifier = CommPortIdentifier.getPortIdentifier(portName)
    val os = if (portIdentifier.isCurrentlyOwned) {
        println("Error: Port is currently in use")
        return
    } else {
        val commPort = portIdentifier.open("Simulator", 2000)
        (commPort as SerialPort).setSerialPortParams(4800, DATABITS_8, STOPBITS_1, PARITY_NONE)
        println("done.")

        commPort.outputStream
    }

    reader.lines().forEach {
        if (it.trim().isNotEmpty() && !it.startsWith('#')) {
            val indexOfBytes = it.indexOf(": ") + 2
            val currTime = ISO_TIME.parse(it.substring(0, indexOfBytes - 2)).getLong(MILLI_OF_DAY)
            if (startTime == -1L) {
                startTime = currTime
                lastTime = currTime
            }

            sleep(currTime - lastTime)
            lastTime = currTime

            println(it)
            os.write(parseBytes(it.substring(indexOfBytes)))
        }
    }
}

fun parseBytes(byteStr: String) : ByteArray {
    val result: MutableList<Byte> = mutableListOf()

    for (it in byteStr.split(" ")) {
        try {
            result.add(Integer.parseInt(it, 16).toByte())
        } catch (e: NumberFormatException) {
            // the first non-parseable string is treated as the beginning of the comment
            break
        }
    }

    return result.toByteArray()
}

