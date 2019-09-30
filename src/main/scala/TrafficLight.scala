
import ActorUtils._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorSystem, Behavior, SupervisorStrategy}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import akka.persistence.typed.scaladsl.{Effect, EffectBuilder, EventSourcedBehavior}

object TrafficLight extends App {
  val trafficLight = TrafficLight("traffic-light")
  val c = Behaviors.supervise(trafficLight.behavior).onFailure[RuntimeException](SupervisorStrategy.resume)
  val ref = ActorSystem(c, trafficLight.id)

  Thread.sleep(10 * 1000)
  ref ! TrafficCommand()
  Thread.sleep(15 * 1000)
  ref ! TrafficCommand()
  Thread.sleep(3 * 1000)
  ref ! PedestrianCommand()
  Thread.sleep(1 * 1000)
  ref ! ExplodeCommand()
  Thread.sleep(10 * 1000)
  ref ! PedestrianCommand()
  Thread.sleep(3 * 1000)
  ref ! StopCommand()
  Thread.sleep(3 * 1000)
  ref ! StopCommand()

}

case class TrafficLight(id:String) {

  def behavior:Behavior[Command] = Behaviors.setup { ctx =>
    val initial = Red(ctx)
    ctx.log.info(s"initial state ${initial}")
    val canceller = initial.expiryOption.map { expiry => withTimer(expiry, ExpireCommand(), ctx) }
    wrap(initial, ctx)
  }

  def wrap(initial: TrafficLightState, ctx:ActorContext[Command]): Behavior[Command] = {
    EventSourcedBehavior.apply[Command, Command, TrafficLightState] (
      PersistenceId(id),
      initial,
      (current, msg) => {
        val transition = current.onEvent(msg)
        ctx.log.info(s"command handler (${current},${msg}) => Effect")
        val next = transition()
        val builder:EffectBuilder[Command, TrafficLightState] = next match {
          case t:TimedState => {
            t.expiryOption.map { withTimer(_, ExpireCommand(), ctx) }
            Effect.persist(msg)
          }
          case s:Final => Effect.persist[Command, TrafficLightState](msg).thenStop
          case _ => Effect.persist(msg)
        }
        builder.thenRun(transition.sideEffect)
      },
      (current, msg) => {
        val next = current.onEvent(msg)()
        ctx.log.info(s"event handler: (${current}, ${msg}) => ${next}")
        next
      }
    ).receiveSignal {
      case (state, signal@RecoveryCompleted) => {
        ctx.log.info(s"recovery completed: (${state}, ${signal})")
        state match {
          case t:TimedState => t.expiryOption.map { withTimer(_, ExpireCommand(), ctx) }
          case _ =>
        }
      }
      case (state, signal) => {
        ctx.log.info(s"signal received: (${state}, ${signal})")
      }
    }
  }

}
