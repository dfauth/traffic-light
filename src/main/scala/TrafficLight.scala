
import ActorUtils._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
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
    initial.expiryOption.map { expiry => withTimer(expiry, ExpireCommand(), ctx) }
    wrap(initial, ctx)
  }

  def wrap(initial: TrafficLightState, ctx:ActorContext[Command]): Behavior[Command] = {

    var timerActorRef:Option[ActorRef[TimerCommand]] = None

    EventSourcedBehavior.apply[Command, Command, TrafficLightState] (
      PersistenceId(id),
      initial,
      (current, msg) => {
        val transition = current.onEvent(msg)
        ctx.log.info(s"command handler (${current},${msg}) => Effect")
        (current, msg) match {
          case (t:TimedState[Command], e:ExpireCommand) => // expired nothing to do
          case (t:TimedState[Command], _) => {
            timerActorRef.map { t.cancelTimer(_) } // other transition, cancel timer
          }
          case _ => // non-timed state; ignore
        }
        val next = transition()
        val builder:EffectBuilder[Command, TrafficLightState] = next match {
          case t:TimedState[Command] => {
            timerActorRef = t.withTimer(ExpireCommand())
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
          case t:TimedState[Command] => {
            timerActorRef = t.withTimer(ExpireCommand())
          }
          case _ =>
        }
      }
      case (state, signal) => {
        ctx.log.info(s"signal received: (${state}, ${signal})")
      }
    }
  }

}
