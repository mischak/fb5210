/*
 * MIT License
 *
 * Copyright (c) 2023 Mischa Kölliker
 */
package ch.mischak.fb5210.webapp

import org.junit.Assert.assertNotNull
import org.influxdb.InfluxDB
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest
class NetatmoReceiverTest {

    @MockBean
    lateinit var telegramHandler: TelegramHandler

    @MockBean
    lateinit var influxDB: InfluxDB


    @Autowired
    lateinit var netatmoReceiver: NetatmoReceiver

    @Autowired
    lateinit var netatmoProperties: NetatmoProperties

    @Value("\${netatmo_client_id}")
    lateinit var netatmo_client_id : String

    @Value("\${netatmo_client_secret}")
    lateinit var netatmo_client_secret : String

    @Value("\${netatmo_refresh_token}")
    lateinit var netatmo_refresh_token : String

    @Before
    fun setup() {
        netatmoProperties.netatmo_client_id = netatmo_client_id
        netatmoProperties.netatmo_client_secret = netatmo_client_secret
        netatmoProperties.netatmo_refresh_token = netatmo_refresh_token

        netatmoProperties.humidity_unten = null
        netatmoProperties.temp_unten = null
        netatmoProperties.last_seen_unten = null
        netatmoProperties.battery_unten = null
        netatmoProperties.humidity_oben = null
        netatmoProperties.temp_oben = null
        netatmoProperties.last_seen_oben = null
        netatmoProperties.last_retrieved = null
    }

    @Test
    fun testRetrieveTemperatures() {
        netatmoReceiver.retrieveTemperatures0()

        assertNotNull(netatmoProperties.humidity_unten)
        assertNotNull(netatmoProperties.temp_unten)
        assertNotNull(netatmoProperties.last_seen_unten)
        assertNotNull(netatmoProperties.battery_unten)
        assertNotNull(netatmoProperties.humidity_oben)
        assertNotNull(netatmoProperties.temp_oben)
        assertNotNull(netatmoProperties.last_seen_oben)
        assertNotNull(netatmoProperties.last_retrieved)
        assertNotNull(netatmoProperties.netatmo_access_token)
    }
}
