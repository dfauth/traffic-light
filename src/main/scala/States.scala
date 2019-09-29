import java.time.{Duration, LocalDateTime}

import com.typesafe.scalalogging.LazyLogging

object TrafficLightState {
  val STANDARD = 10
  def withDuration(d:Int = STANDARD) = Some(LocalDateTime.now().plus(Duration.ofSeconds(d)))

  type SideEffect = TrafficLightState => Unit
  trait Transition extends Function0[TrafficLightState] {
    val sideEffect:SideEffect
  }

  val noOpSideEffect:SideEffect = _ => {}

  case class TransitionBuilder(next:TrafficLightState, sideEffectOption:Option[SideEffect] = None) extends Transition {
    override def apply() = next
    override val sideEffect: SideEffect = sideEffectOption.getOrElse(noOpSideEffect)
    def withSideEffect(sideEffect:SideEffect):TransitionBuilder = TransitionBuilder(next, Some(sideEffect))
  }

}

sealed trait Final

sealed trait TrafficLightState extends LazyLogging{
  import TrafficLightState._
  protected def _onEvent:PartialFunction[Command, Transition] = PartialFunction.empty[Command, Transition]
  protected def _defaults = {
    case StopCommand => transitionTo(Extinguished).withSideEffect( _ => logger.info(s"extinguished"))
    case ExplodeCommand => throw new RuntimeException("Oops")
  }:PartialFunction[Command, Transition]
  def transitionTo(next:TrafficLightState):TransitionBuilder = TransitionBuilder(next)

  def noTransition(): Transition = new Transition {
    override val sideEffect: SideEffect = noOpSideEffect
    override def apply(): TrafficLightState = TrafficLightState.this
  }

  def onEvent(cmd:Command):Transition = {
    _onEvent.orElse(_defaults).applyOrElse(cmd, (cmd: Command) => {
      logger.info(s"unhandled command ${cmd}")
      noTransition()
    })
  }
}

trait TimedTrafficLightState extends TrafficLightState with TimedState

case class Red(expiryOption:Option[LocalDateTime] = None) extends TimedTrafficLightState {
  override def _onEvent = {
    case c@ExpireCommand  => transitionTo(Green()).withSideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
    }
    case c@TrafficCommand => transitionTo(Green()).withSideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
    }
  }
}

case class Yellow(expiryOption:Option[LocalDateTime] = TrafficLightState.withDuration(2)) extends TimedTrafficLightState {
  override def _onEvent = {
    case c@PedestrianCommand => transitionTo(Red()).withSideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
    }
    case c@ExpireCommand => transitionTo(Red()).withSideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
    }
  }
}

case class Green(expiryOption:Option[LocalDateTime] = TrafficLightState.withDuration()) extends TimedTrafficLightState {
  override def _onEvent = {
    case c@PedestrianCommand => transitionTo(Yellow()).withSideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
    }
    case c@ExpireCommand => transitionTo(Yellow()).withSideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
    }
  }
}

case object Extinguished extends TrafficLightState with Final
