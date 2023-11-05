/*
 * MIT License
 *
 * Copyright (c) 2023 Mischa Kölliker
 */
package ch.mischak.fb5210.webapp

import ch.mischak.fb5210.crc.AlgoParams
import ch.mischak.fb5210.crc.CrcCalculator
import ch.mischak.fb5210.webapp.Heizkreis.UNTEN
import gnu.io.CommPort
import gnu.io.CommPortIdentifier
import gnu.io.SerialPort
import gnu.io.SerialPort.DATABITS_8
import gnu.io.SerialPort.PARITY_NONE
import gnu.io.SerialPort.STOPBITS_1
import org.influxdb.BatchOptions
import org.influxdb.InfluxDB
import org.influxdb.InfluxDBFactory
import org.influxdb.dto.Query
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Primary
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor


@SpringBootApplication
@EnableScheduling
@EnableAsync
open class Application : AsyncConfigurer {
    val log = LoggerFactory.getLogger(this.javaClass.name)

    companion object {
        val DEFAULT_DEVICE = "/dev/ttyUSB0"
    }

    @Autowired
    lateinit var telegramHandler : TelegramHandler

    @EventListener(ApplicationReadyEvent::class)
    fun initAfterStartup() {
        log.info("Starting Async from thread: " + Thread.currentThread().name)

        telegramHandler.process();
    }

    override fun getAsyncExecutor(): Executor? {
        log.info("getAsyncExecutor(): called")

        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 3
        executor.maxPoolSize = 5
        executor.threadNamePrefix = "Spring-"
        executor.initialize()
        return executor
    }

    @Bean @Primary @Lazy
    open fun getInfluxDB() : InfluxDB {
        log.info("getInfluxDB(): called")
        val db = InfluxDBFactory.connect("http://localhost:8086")

        val result = db.query(Query("SHOW DATABASES", null))
        // {"results":[{"series":[{"name":"databases","columns":["name"],"values":[["mydb"]]}]}]}
        // Series [name=databases, columns=[name], values=[[mydb], [unittest_1433605300968]]]
        log.info("Result from SHOW DATABASES query: $result")
        if (result.results[0].series[0].values?.find { it.first().toString() == "heizung" } == null) {
            log.info("getInfluxDB(): creating database 'heizung'")
            db.query(Query("""CREATE DATABASE "heizung"""", null, true))
        }

        db.setDatabase("heizung")
        db.enableBatch(BatchOptions.DEFAULTS.flushDuration(10000))
        return db
    }

    @Bean @Lazy
    open fun getCommPort() : CommPort {
        val serialDevice = System.getProperty("serial.device", DEFAULT_DEVICE)
        log.info("Opening port on device $serialDevice...")

        val portIdentifier = CommPortIdentifier.getPortIdentifier(serialDevice)
        if (portIdentifier.isCurrentlyOwned) {
            log.error("Port is currently in use")
            throw Exception("Port '$serialDevice' is currently in use")
        } else {
            val commPort = portIdentifier.open(this.javaClass.name, 2000)
            if (commPort is SerialPort) {
                commPort.setSerialPortParams(4800, DATABITS_8, STOPBITS_1, PARITY_NONE)
                return commPort
            } else {
                throw Exception("Port '$serialDevice' is not a serial port")
            }
        }
    }

    @Bean @Lazy @Primary
    open fun getHeizungProperties() : HeizungProperties {
        return HeizungProperties(Heizkreis.fromSystemProperty()).init()
    }

    @Bean @Lazy @HeizungUntenProperties
    open fun getHeizungUntenProperties() : HeizungProperties {
        return HeizungProperties(UNTEN).init()
    }

    @Bean
    open fun getCrcCalculator() : CrcCalculator {
        return CrcCalculator(AlgoParams(8, 0xD5, 0x0, true, true, 0x0))
    }
}



fun main(args: Array<String>) {
    SpringApplication.run(Application::class.java, *args)
}

