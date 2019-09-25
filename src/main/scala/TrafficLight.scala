import java.time.{Duration, LocalDateTime}
import java.util.concurrent.TimeUnit

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorSystem, Behavior, SupervisorStrategy}
import Utils._
import akka.actor.typed.scaladsl.Behaviors.Receive
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration.FiniteDuration

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
}

case class TrafficLight() {

  def behavior:Behavior[Command] = Behaviors.setup { ctx =>
    val initial:TrafficLightState = Red()
    ctx.log.info(s"initial state ${initial}")
    Behaviors.withTimers[Command] { timer =>
      initial.expiry.map { expiry =>
        val delay = Duration.between(LocalDateTime.now(), expiry)
        timer.startSingleTimer(id, ExpireCommand, FiniteDuration(delay.toMillis, TimeUnit.MILLISECONDS))
      }
      val b = toBehavior(initial, initial)
      wrap(b, initial)
    }
  }

  def toBehavior(initial: TrafficLightState, current: TrafficLightState): AbstractBehavior[Command] = new AbstractBehavior[Command] {
    override def onMessage(msg: Command): Behavior[Command] = {
      val next = current.onEvent(msg)
      Behaviors.withTimers[Command] { timer =>
        next.expiry.map { expiry =>
          val delay = Duration.between(LocalDateTime.now(), expiry)
          timer.startSingleTimer(id, ExpireCommand, FiniteDuration(delay.toMillis, TimeUnit.MILLISECONDS))
        }
        val b = toBehavior(initial, next)
        wrap(b, initial)
      }
    }
  }

  def wrap(b:AbstractBehavior[Command], initial: TrafficLightState): Behavior[Command] = b

  def wrap1(b:AbstractBehavior[Command], initial: TrafficLightState): Behavior[Command] = EventSourcedBehavior.apply[Command, Command, Behavior[Command]] (
      PersistenceId(id),                            // persistenceId
      toBehavior(initial, initial),                 // initial state
      (s, c) => {                                   // command handler
            val effect:Effect[Command, Behavior[Command]] = Effect.persist(c)
//            b.onMessage(c)
            effect
          },
      (s, e) => b.onMessage(e)                      // event handler
    )
}
