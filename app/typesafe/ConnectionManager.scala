package typesafe

import akka.actor.{ActorSelection, ActorRef, Actor, ActorLogging}
import typesafe.ConnectionManager.{Idle, WorkerState, SendWork}
import scala.concurrent.duration.Deadline
import akka.cluster.Cluster
import typesafe.Messages.AckSendWork
import akka.cluster.ClusterEvent.{MemberUp, MemberEvent}

class ConnectionManager extends Actor with ActorLogging {


  val cluster = Cluster(context.system)
  val connectionManagerAddress = cluster.selfAddress
  println(s"connection manager running on ${cluster.selfAddress}")

  var connectionManagerMemberUID:Option[String] = None


  private var workers = Map[String, WorkerState]()

  //This is a hack - I would like to find a better way of doing this.
  //We need to get a member uid, so we listen to cluster events
  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberEvent])

  override def postStop(): Unit = cluster.unsubscribe(self)


  override def receive: Actor.Receive = {
    case sendWork@SendWork(work) =>
      val connectionWorker = context.actorOf(ConnectionWorker.props())
      val connectionWorkerName: String = connectionWorker.path.name
      workers += (connectionWorkerName -> WorkerState(connectionWorker, Idle))
      println(s"sending work to $connectionWorkerName at address ${connectionWorker.path.address}")
      connectionWorker ! sendWork
      context.system.actorSelection("/user/clusterManager") ! AckSendWork(work, connectionManagerAddress)

    case MemberUp(member) if (member.address == connectionManagerAddress) =>
      connectionManagerMemberUID = Some(member.hashCode().toString)
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