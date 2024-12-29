package com.wyg.smart_man.utils

object ParamsConstants {
    const val HOST_ADDRESS = "192.168.1.100"

    const val PORT_OMANI = 2017
    const val PORT_LASER = 9981

    const val COMMAND_CONTROL_SEND_INTERVAL = 100L
    const val COMMAND_QUERY_SEND_INTERVAL = 1000L
}

object UiConstants {

    const val CONNECT = "connect"
    const val DISCONNECT = "disconnect"
    const val AUTO_CONTROL = "auto control"
    const val MANUAL_CONTROL = "manual control"
    const val OMNI_PARAMS = "omni params"
    const val ALARM_INFO = "alarm info"
    const val BASE_SOC = "base soc"
    const val BASE_SPEED = "base speed"
}

object MsgConstants {

    const val MSG_ACTIVITY = 1
    const val MSG_SERVICE = 2

    const val CLIENT_ARG = 0x13
    const val CLIENT_INFO = 0x14
}


object ProtocolCommand {
    const val AUTO_OR_MANUAL = "8"
    const val MANUAL_CONTROL_SPEED = "17"
    const val QUERY_BATTERYINFO = "18"
}