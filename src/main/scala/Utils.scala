import java.time.{Duration, LocalDateTime}
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.receptionist.Receptionist.Subscribe
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.FiniteDuration

object Utils {

  def id() = UUID.randomUUID().getLeastSignificantBits.toHexString

  val done:Future[Done] = Promise[Done].success(Done).future

}

trait TimedState[T] extends LazyLogging {
  val ctx:ActorContext[T]
  val expiryOption:Option[LocalDateTime]
  def withTimer[U <: ExpireTimerCommand](cmd:U with T) = {
    expiryOption.map { e =>
      ActorUtils.withTimer(e, cmd, ctx)
    }
  }

  def cancelTimer(ref:ActorRef[TimerCommand]):Unit = {
    ref ! InternalCancelTimerCommand
  }
}

trait TimerCommand

trait ExpireTimerCommand extends TimerCommand
trait CancelTimerCommand extends TimerCommand
case object InternalCancelTimerCommand extends CancelTimerCommand

object ActorUtils {

  def withTimer[T, U <: ExpireTimerCommand](expiry:LocalDateTime, cmd:U with T, ctx:ActorContext[T]):ActorRef[TimerCommand] = {
    val timerId = "timer"
    val b = Behaviors.withTimers[TimerCommand] { timer =>
      val delay = Duration.between(LocalDateTime.now(), expiry)
      if(delay.isNegative) {
        ctx.self ! cmd
        Behaviors.stopped[TimerCommand]
      } else {
        timer.startSingleTimer(timerId, cmd, FiniteDuration(delay.toMillis, TimeUnit.MILLISECONDS))
        Behaviors.receive[TimerCommand] { (_, msg) =>
          msg match {
            case c:ExpireTimerCommand => ctx.self ! cmd
              Behaviors.stopped[TimerCommand]
            case c:CancelTimerCommand => {
              timer.cancel(timerId)
              ctx.log.info(s"cancelled timer with id: ${timerId}")
              Behaviors.stopped[TimerCommand]
            }
            case _ =>  Behaviors.unhandled[TimerCommand]
          }
        }
      }
    }
    ctx.spawnAnonymous(b)
  }
}
