package com.example

import akka.actor.typed.ActorSystem

object IotApp {

    def main(args: Array[String]): Unit = {
        // Create ActorSystem and top level supervisor
        ActorSystem[Nothing](IotSupervisor(), "iot-system")
    }

}