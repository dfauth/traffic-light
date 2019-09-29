
import Utils._
import ActorUtils._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorSystem, Behavior, SupervisorStrategy}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EffectBuilder, EventSourcedBehavior}

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
      (current, msg) => {
        val transition = current.onEvent(msg)
        ctx.log.info(s"command handler (${current},${msg}) => Effect")
        val builder:EffectBuilder[Command, TrafficLightState] = transition() match {
          case s:Final => Effect.persist[Command, TrafficLightState](msg).thenStop
          case _ => Effect.persist(msg)
        }
        builder.thenRun(transition.sideEffect)
      },
      (current, msg) => {
        val transition = current.onEvent(msg)
        val next = transition()
        next match {
          case s:TimedState => s.expiryOption.map { expiry => withTimer(expiry, ExpireCommand, ctx)}
          case _ =>
        }
        next
      }
    )
  }

}
