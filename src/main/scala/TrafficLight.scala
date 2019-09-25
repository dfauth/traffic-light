import java.time.{Duration, LocalDateTime}
import java.util.concurrent.TimeUnit

import Utils._
import akka.actor.typed.scaladsl.{AbstractBehavior, Behaviors}
import akka.actor.typed.{ActorSystem, Behavior, SupervisorStrategy}
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

  def possiblyWithTimer(state: TrafficLightState, f:TrafficLightState => Behavior[Command]): Behavior[Command] = {
    state.expiry.map { expiry =>
      Behaviors.withTimers[Command] { timer =>
        val delay = Duration.between(LocalDateTime.now(), expiry)
        timer.startSingleTimer(id, ExpireCommand, FiniteDuration(delay.toMillis, TimeUnit.MILLISECONDS))
        f(state)
      }
    }.getOrElse {
      f(state)
    }
  }

  def behavior:Behavior[Command] = Behaviors.setup { ctx =>
    val initial:TrafficLightState = Red()
    ctx.log.info(s"initial state ${initial}")
    possiblyWithTimer(initial, toBehavior)
  }

  def toBehavior(current: TrafficLightState): Behavior[Command] = new AbstractBehavior[Command] {
    override def onMessage(msg: Command): Behavior[Command] = {
      val next = current.onEvent(msg)
      possiblyWithTimer(next, toBehavior)
    }
  }

  def wrap(initial: TrafficLightState): Behavior[Command] = {
    EventSourcedBehavior.apply[Command, Command, TrafficLightState] (
      PersistenceId(id),
      initial,
      (s, c) => {
        Effect.persist(c)
      },
      (current, msg) => {
        val next = current.onEvent(msg)
        possiblyWithTimer(next, wrap)
        next
      }
    )
  }
}
