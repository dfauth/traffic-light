import java.util.UUID

object Utils {

  def id() = UUID.randomUUID().getLeastSignificantBits.toHexString

}
