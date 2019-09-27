import java.time.{Duration, LocalDateTime}
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

import scala.concurrent.duration.FiniteDuration

object Utils {

  def id() = UUID.randomUUID().getLeastSignificantBits.toHexString

}

trait TimedState {
  val expiryOption:Option[LocalDateTime]
}

trait TimerCommand

trait ExpireTimerCommand extends TimerCommand
trait CancelTimerCommand extends TimerCommand
case object InternalCancelTimerCommand extends TimerCommand

object ActorUtils {

  import Utils._

  def withTimer[T, U <: ExpireTimerCommand](expiry:LocalDateTime, cmd:U with T, ctx:ActorContext[T]) = {
    val timerId = id
    val b = Behaviors.withTimers[TimerCommand] { timer =>
      val delay = Duration.between(LocalDateTime.now(), expiry)
      timer.startSingleTimer(timerId, cmd, FiniteDuration(delay.toMillis, TimeUnit.MILLISECONDS))
      Behaviors.receive[TimerCommand] { (_, msg) =>
        ctx.log.info(s"received message ${msg}")
        msg match {
          case c:ExpireTimerCommand => ctx.self ! cmd
            Behaviors.stopped[TimerCommand]
          case c:CancelTimerCommand => timer.cancel(timerId)
            Behaviors.stopped[TimerCommand]
          case _ =>                    Behaviors.unhandled[TimerCommand]
        }
      }
    }
    val ref = ctx.spawnAnonymous(b)
    () => ref ! InternalCancelTimerCommand
  }


}
