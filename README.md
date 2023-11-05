# FB5210

Heating remote controller that emulates an FB5210 device.

The FB5210 device is connected to the heating controller via a 12V 2-wire cable, which acts as power-supply and data transmission line at the same time. The heating controller delivers at most 50mA. To create a signal `low` level, the two wires need to be short-circuit.

The protocol is RS-232 with a 1-byte checksum as the last byte.

My setup runs on a Raspberry Pi with Raspbian. Room temperatures are delivered by a Netatmo device.

Temperature values are stored in an InfluxDB.

With a Grafana dashboard, current temperatures, as well as history data is displayed. With some active elements it is possible to set the desired temperature on the dashboard.

To compile and operate, the libraries RXTXComm.jar and librxtxSerial.so are required. Since they are not open source, I did not include them in this repo.

Most of the code is written in Kotlin. The application has been written in 2018/19. All library- and tool versions are the current ones from that time.

The code is most probably not ready to run without modification. There is a lot of stuff hardcoded for my exact environment and heating configuration.

In my setup, two instances of the main application (`webapp`) are run, one for each heating circuit ("Heizkreis").

More info can be found here (in german) :https://www.haustechnikdialog.de/Forum/p/2738217/

Some abbrevations:

* WVF: Wärmeverteilmodul
* UML: Umälzpumpe (Heizungspumpe), Motormischer, (Boiler)Ladepumpe
* BUL: Brenneransteuerung, Umälzpumpe (Heizungspumpe), (Boiler)Ladepumpe
