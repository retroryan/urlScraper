package typesafe

import akka.actor._
import scala.concurrent.duration.Deadline
import akka.cluster.Cluster
import java.util.UUID
import typesafe.ConnectionManager.{Idle, WorkerState, SendWork}
import typesafe.ConnectionServiceSingleton.RegisterWork
import typesafe.ConnectionServiceSingleton.RegisterConnectionManager

class ConnectionManager extends Actor with ActorLogging {

  val workerId = UUID.randomUUID().toString

  val clusterSingletonProxy = context.system.actorSelection("/user/clusterSingletonProxy")
  clusterSingletonProxy ! RegisterConnectionManager

  val cluster = Cluster(context.system)
  log.info(s"started workerId = $workerId on ${cluster.selfAddress}  and proxy = $clusterSingletonProxy")

  override def receive: Actor.Receive = {
    case sendWork@SendWork(work) => {
      log.info(s"starting work on ${work.url} at address ${self.path}")
      clusterSingletonProxy ! RegisterWork(work)
    }
  }
}

object ConnectionManager {

  case class SendWork(work: ConnectionWorkRequest)

  case class JobFailed(reason: String)

  private sealed trait WorkerStatus

  private case object Idle extends WorkerStatus

  private case class Busy(work: ConnectionWorkRequest, deadline: Deadline) extends WorkerStatus

  private case class WorkerState(ref: ActorRef, status: WorkerStatus)


}