
import Utils._
import ActorUtils._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorSystem, Behavior, SupervisorStrategy}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

object TrafficLight extends App {
  val b = TrafficLight().behavior
  val c = Behaviors.supervise(b).onFailure[RuntimeException](SupervisorStrategy.restart)
  val ref = ActorSystem(c, "traffic-light")
  Thread.sleep(10 * 1000)
  ref ! TrafficCommand
  Thread.sleep(15 * 1000)
  ref ! TrafficCommand
  Thread.sleep(3 * 1000)
  ref ! PedestrianCommand
  Thread.sleep(1 * 1000)
  ref ! ExplodeCommand
  Thread.sleep(10 * 1000)
  ref ! PedestrianCommand
  Thread.sleep(3 * 1000)
  ref ! StopCommand
  Thread.sleep(3 * 1000)
  ref ! StopCommand
}

case class TrafficLight() {

  def behavior:Behavior[Command] = Behaviors.setup { ctx =>
    val initial = Red()
    ctx.log.info(s"initial state ${initial}")
    val canceller = initial.expiryOption.map { expiry => withTimer(expiry, ExpireCommand, ctx) }
    wrap(initial, ctx)
  }

  def wrap(initial: TrafficLightState, ctx:ActorContext[Command]): Behavior[Command] = {
    EventSourcedBehavior.apply[Command, Command, TrafficLightState] (
      PersistenceId(id),
      initial,
      (s, c) => {
        ctx.log.info(s"persist ${c}")
        s match {
          case _:Final => Effect.stop()
          case _ => Effect.persist(c)
        }
      },
      (current, msg) => {
        val next = current.onEvent(msg)
        next match {
            case s:TimedState => s.expiryOption.map { expiry => withTimer(expiry, ExpireCommand, ctx)}
            case _ =>
        }
        next
      }
    )
  }
}
