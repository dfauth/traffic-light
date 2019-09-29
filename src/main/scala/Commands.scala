sealed trait Command extends Serializable

case object ExpireCommand extends Command with ExpireTimerCommand
case object PedestrianCommand extends Command
case object TrafficCommand extends Command
case object ExplodeCommand extends Command
case object StopCommand extends Command
