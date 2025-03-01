package com.wyg.smart_man.utils

object ParamsConstants {
    const val HOST_ADDRESS = "192.168.1.100"

    const val PORT_OMANI = 2017
    const val PORT_LASER = 9981
    const val PORT_HAND = 5000
    const val PORT_SPEAK = 5001

    const val COMMAND_CONTROL_SEND_INTERVAL = 100L
    const val COMMAND_QUERY_SEND_INTERVAL = 1000L
}

object UiConstants {

    const val OMNICONNECT = "omniConnect"
    const val OMNIDISCONNECT = "omniDisconnect"
    const val LASERCONNECT = "laserConnect"
    const val LASERDISCONNECT = "laserDisconnect"
    const val AUTOCONTROL = "autoControl"
    const val MANUALCONTROL = "manualControl"
    const val OMNIPARAMS = "omniParams"
    const val ALARMINFO = "alarmInfo"
    const val BASESOC = "baseSoc"
    const val XSPEED= "xSpeed"
    const val YSPEED= "ySpeed"
    const val OMEGA= "oMega"
    const val LASERPARAMS = "laserParams"
    const val XCOORDINATE= "xCoordinate"
    const val YCOORDINATE = "yCoordinate"
    const val THETA = "theta"
    const val XVELOCITY = "xVelocity"
    const val EMERGENCYSTOP = "emergencyStop"
    const val NAVSTATUS = "navStatus"

}

object MsgConstants {

    const val MSG_ACTIVITY = 1
    const val MSG_SERVICE = 2

    const val CLIENT_ARG = 0x13
    const val CLIENT_INFO = 0x14
}


object OmniProtocolCommand {
    const val UPLOAD_STATUS: Byte = 0x03
    const val AUTO_OR_MANUAL: Byte = 0x08
    const val MANUAL_CONTROL_LIGHT: Byte = 0x10
    const val MANUAL_CONTROL_SPEED: Byte = 0x11
    const val QUERY_BATTERYINFO: Byte = 0x12
}

object LaserProtocolCommand {
    const val UPLOAD_LOCATION: Int = 0x00
    const val SET_DESTINATION: Int = 0x03
    const val SET_DESTINATION_ACK: Int = 0x04
    const val SET_STOP: Int = 0x05
    const val SET_STOP_ACK: Int = 0x06
    const val SET_START: Int = 0x07
    const val SET_START_ACK: Int = 0x08
    const val FAIL_PLANNING:Int = 0x0a
}