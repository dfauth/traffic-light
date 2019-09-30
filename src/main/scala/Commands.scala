import java.time.LocalDateTime

sealed trait Command extends Serializable {
  val timestamp:LocalDateTime
}

case class ExpireCommand(timestamp:LocalDateTime = LocalDateTime.now) extends Command with ExpireTimerCommand
case class PedestrianCommand(timestamp:LocalDateTime = LocalDateTime.now) extends Command
case class TrafficCommand(timestamp:LocalDateTime = LocalDateTime.now) extends Command
case class ExplodeCommand(timestamp:LocalDateTime = LocalDateTime.now) extends Command
case class StopCommand(timestamp:LocalDateTime = LocalDateTime.now) extends Command
