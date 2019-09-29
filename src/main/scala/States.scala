import java.time.{Duration, LocalDateTime}

import ActorUtils.withTimer
import akka.actor.typed.scaladsl.ActorContext
import com.typesafe.scalalogging.LazyLogging

object TrafficLightState {
  val STANDARD = 10
  def withDuration(d:Int = STANDARD) = Some(LocalDateTime.now().plus(Duration.ofSeconds(d)))

  type SideEffect = TrafficLightState => Unit
  trait Transition extends Function0[TrafficLightState] {
    val sideEffect:SideEffect
  }

  val noOpSideEffect:SideEffect = _ => {}

  case class TransitionBuilder(from:TrafficLightState, to:TrafficLightState, sideEffectSeq:Seq[SideEffect] = Seq.empty) extends Transition {
    override def apply() = to
    override val sideEffect: SideEffect = reduce(from.onExit() +: sideEffectSeq :+ to.onEntry())
    def withSideEffect(sideEffect:SideEffect):TransitionBuilder = TransitionBuilder(from, to, sideEffectSeq :+ sideEffect)
    private def reduce(sideEffects:Seq[SideEffect]):SideEffect = s => sideEffects.foreach(_(s))
  }

}

sealed trait Final

sealed trait TrafficLightState extends LazyLogging{
  import TrafficLightState._
  val ctx:ActorContext[Command]
  val expiryOption:Option[LocalDateTime]
  protected def _onEvent:PartialFunction[Command, Transition] = PartialFunction.empty[Command, Transition]
  protected def _defaults = {
    case StopCommand => transitionTo(Extinguished(ctx)).withSideEffect( _ => logger.info(s"extinguished"))
    case ExplodeCommand => throw new RuntimeException("Oops")
  }:PartialFunction[Command, Transition]
  def transitionTo(next:TrafficLightState):TransitionBuilder = TransitionBuilder(this, next)

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

  private var cancelTimer:() => Unit = () => Unit

  protected def reset:Unit = cancelTimer = () => Unit

  def onEntry():SideEffect = s => {
    expiryOption.foreach { expiry =>
      cancelTimer = withTimer(expiry, ExpireCommand, ctx)
    }
  }

  def onExit():SideEffect = s => expiryOption.foreach {_ => cancelTimer() }

  def red:Red = Red(ctx)
  def yellow:Yellow = Yellow(ctx)
  def green:Green = Green(ctx)
}

case class Red(ctx:ActorContext[Command], expiryOption:Option[LocalDateTime] = None) extends TrafficLightState {
  override def _onEvent = {
    case c@ExpireCommand  => reset; transitionTo(green).withSideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
    }
    case c@TrafficCommand => transitionTo(green).withSideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
    }
  }
}

case class Yellow(ctx:ActorContext[Command], expiryOption:Option[LocalDateTime] = TrafficLightState.withDuration(2)) extends TrafficLightState {
  override def _onEvent = {
    case c@PedestrianCommand => transitionTo(red).withSideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
    }
    case c@ExpireCommand => reset; transitionTo(red).withSideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
    }
  }
}

case class Green(ctx:ActorContext[Command], expiryOption:Option[LocalDateTime] = TrafficLightState.withDuration()) extends TrafficLightState {
  override def _onEvent = {
    case c@PedestrianCommand => transitionTo(yellow).withSideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
    }
    case c@ExpireCommand => reset; transitionTo(yellow).withSideEffect { s =>
      logger.info(s"${c} triggers transition ${this} -> ${s}")
    }
  }
}

case class Extinguished(ctx:ActorContext[Command]) extends TrafficLightState with Final {
  override val expiryOption: Option[LocalDateTime] = None
}
