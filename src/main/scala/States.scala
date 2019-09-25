import java.time.{Duration, LocalDateTime}

import com.typesafe.scalalogging.LazyLogging

object TrafficLightState {
  val STANDARD = 10
  def withDuration(d:Int = STANDARD) = Some(LocalDateTime.now().plus(Duration.ofSeconds(d)))
}

sealed trait TrafficLightState extends LazyLogging {
  val expiry:Option[LocalDateTime]
  def onEvent(cmd:Command):TrafficLightState = {
    logger.info(s"command ${cmd}")
    _onEvent.orElse(_defaults).applyOrElse(cmd, (cmd:Command) => {
      logger.info(s"unhandled command ${cmd}")
      this
    })
  }
  protected def _onEvent:PartialFunction[Command, TrafficLightState]
  protected def _defaults = {
     case ExplodeCommand => throw new RuntimeException("Oops")
  }:PartialFunction[Command, TrafficLightState]
  def sideEffect(f: TrafficLightState => Unit):TrafficLightState = {
    f(this)
    this
  }
}

case class Red(expiry:Option[LocalDateTime] = None) extends TrafficLightState {
  override def _onEvent = {
    case c@ExpireCommand  => Green().sideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
    }
    case c@TrafficCommand => Green().sideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
    }
  }
}

case class Yellow(expiry:Option[LocalDateTime] = TrafficLightState.withDuration(2)) extends TrafficLightState {
  override def _onEvent = {
    case c@PedestrianCommand => Red().sideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
    }
    case c@ExpireCommand => Red().sideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
    }
  }
}

case class Green(expiry:Option[LocalDateTime] = TrafficLightState.withDuration()) extends TrafficLightState {
  override def _onEvent = {
    case c@PedestrianCommand => Yellow().sideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
    }
    case c@ExpireCommand => Yellow().sideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
    }
  }
}