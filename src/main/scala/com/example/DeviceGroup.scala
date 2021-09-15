package com.example

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import com.example.DeviceManager.RequestAllTemperatures

import scala.concurrent.duration.DurationInt

object DeviceGroup {
    def apply(groupId: String): Behavior[Command] =
        Behaviors.setup(context => new DeviceGroup(context, groupId))

    trait Command

    private final case class DeviceTerminated(device: ActorRef[Device.Command], groupId: String, deviceId: String)
        extends Command

}

class DeviceGroup(context: ActorContext[DeviceGroup.Command], groupId: String)
    extends AbstractBehavior[DeviceGroup.Command](context) {

    import DeviceGroup._
    import DeviceManager.{DeviceRegistered, ReplyDeviceList, RequestDeviceList, RequestTrackDevice}

    private var deviceIdToActor = Map.empty[String, ActorRef[Device.Command]]

    context.log.info("DeviceGroup {} started", groupId)

    override def onMessage(msg: Command): Behavior[Command] =
        msg match {
            case trackMsg@RequestTrackDevice(`groupId`, deviceId, replyTo) =>
                deviceIdToActor.get(deviceId) match {
                    case Some(deviceActor) =>
                        replyTo ! DeviceRegistered(deviceActor)
                    case None =>
                        context.log.info("Creating device actor for {}", trackMsg.deviceId)
                        val deviceActor = context.spawn(Device(groupId, deviceId), s"device-$deviceId")
                        context.watchWith(deviceActor, DeviceTerminated(deviceActor, groupId, deviceId))
                        deviceIdToActor += deviceId -> deviceActor
                        replyTo ! DeviceRegistered(deviceActor)
                }
                this

            case RequestTrackDevice(gId, _, _) =>
                context.log.warn("Ignoring TrackDevice request for {}. This actor is responsible for {}.", gId, groupId)
                this

            case RequestDeviceList(requestId, gId, replyTo) =>
                if (gId == groupId) {
                    replyTo ! ReplyDeviceList(requestId, deviceIdToActor.keySet)
                    this
                } else
                    Behaviors.unhandled

            case DeviceTerminated(_, _, deviceId) =>
                context.log.info("Device actor for {} has been terminated", deviceId)
                deviceIdToActor -= deviceId
                if (deviceIdToActor.isEmpty) {
                    Behaviors.stopped
                } else {
                    this
                }
            case RequestAllTemperatures(requestId, gId, replyTo) =>
                if (gId == groupId) {
                    context.spawnAnonymous(
                        DeviceGroupQuery(deviceIdToActor, requestId = requestId, requester = replyTo, 3.seconds))
                    this
                } else
                    Behaviors.unhandled

        }

    override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
        case PostStop =>
            context.log.info("DeviceGroup {} stopped", groupId)
            this
    }
}

