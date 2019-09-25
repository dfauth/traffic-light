sealed trait Command extends Serializable

case object ExpireCommand extends Command
case object PedestrianCommand extends Command
case object TrafficCommand extends Command
case object ExplodeCommand extends Command
