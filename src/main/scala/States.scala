import java.time.{Duration, LocalDateTime}

import TrafficLightState.{Transition, TransitionBuilder}
import akka.Done
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import Utils._

object TrafficLightState {
  val STANDARD = 10
  def withDuration(d:Int = STANDARD) = Some(LocalDateTime.now().plus(Duration.ofSeconds(d)))

  type SideEffect = TrafficLightState => Future[Done]
  type Transition = TrafficLightState => (TrafficLightState, SideEffect)

  val emptySideEffect:SideEffect = _ => done
  val NoTransition:Transition = s => (s, emptySideEffect)

  case class TransitionBuilder(next:TrafficLightState, sideEffectOption:Option[SideEffect] = None) extends Transition {
    override def apply(current:TrafficLightState) = sideEffectOption.map {
      s => (next, s)
    }.getOrElse {
      (next, emptySideEffect)
    }
    def withSideEffect(sideEffect:SideEffect):TransitionBuilder = TransitionBuilder(next, Some(sideEffect))
  }

}

sealed trait TrafficLightState extends LazyLogging{
  protected def _onEvent:PartialFunction[Command, Transition] = PartialFunction.empty[Command, Transition]
  protected def _defaults = {
    case StopCommand => _:TrafficLightState => (Extinguished, _ => {logger.info(s"extinguished"); done})
    case ExplodeCommand => throw new RuntimeException("Oops")
  }:PartialFunction[Command, Transition]
  def transitionTo(next:TrafficLightState):TransitionBuilder = TransitionBuilder(next)
  def onEvent(cmd:Command):Transition = {
    logger.info(s"command ${cmd}")
    _onEvent.orElse(_defaults).applyOrElse(cmd, (cmd: Command) => {
      logger.info(s"unhandled command ${cmd}")
      TrafficLightState.NoTransition
    })
  }
}

trait TimedTrafficLightState extends TrafficLightState with TimedState

case class Red(expiryOption:Option[LocalDateTime] = None) extends TimedTrafficLightState {
  override def _onEvent = {
    case c@ExpireCommand  => transitionTo(Green()).withSideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
      done
    }
    case c@TrafficCommand => transitionTo(Green()).withSideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
      done
    }
  }
}

case class Yellow(expiryOption:Option[LocalDateTime] = TrafficLightState.withDuration(2)) extends TimedTrafficLightState {
  override def _onEvent = {
    case c@PedestrianCommand => transitionTo(Red()).withSideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
      done
    }
    case c@ExpireCommand => transitionTo(Red()).withSideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
      done
    }
  }
}

case class Green(expiryOption:Option[LocalDateTime] = TrafficLightState.withDuration()) extends TimedTrafficLightState {
  override def _onEvent = {
    case c@PedestrianCommand => transitionTo(Yellow()).withSideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
      done
    }
    case c@ExpireCommand => transitionTo(Yellow()).withSideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
      done
    }
  }
}

case object Extinguished extends TrafficLightState
