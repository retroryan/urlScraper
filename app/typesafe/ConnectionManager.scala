package typesafe

import akka.actor.{ActorRef, Actor, ActorLogging}
import typesafe.ConnectionManager.{Idle, WorkerState, SendWork}
import scala.concurrent.duration.Deadline
import akka.cluster.Cluster

class ConnectionManager extends Actor with ActorLogging {


  val cluster = Cluster(context.system)
  println(s"connection manager running on ${cluster.selfAddress}")


  private var workers = Map[String, WorkerState]()

  //private var

  override def receive: Actor.Receive = {
    case sendWork @ SendWork(work) =>
      val connectionWorker = context.actorOf(ConnectionWorker.props())
      val connectionWorkerName: String = connectionWorker.path.name
      workers += (connectionWorkerName -> WorkerState(connectionWorker, Idle))
      println(s"sending work to $connectionWorkerName at address ${connectionWorker.path.address}")
      connectionWorker ! sendWork
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