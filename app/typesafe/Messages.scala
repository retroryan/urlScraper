package typesafe

import akka.actor.Address
import scala.collection.immutable.HashSet

/**
 * Created by rknight on 4/3/14.
 */
object Messages {

  case class AckSendWork(work: ConnectionWorkRequest, nodeAddress: Address)
  case class RestartConnectionWorkSet(connectionWorkSet: HashSet[ConnectionWorkRequest])


}
