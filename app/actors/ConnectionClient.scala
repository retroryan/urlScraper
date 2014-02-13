package actors

import akka.actor._
import akka.cluster.ClusterEvent.{MemberUp, CurrentClusterState, UnreachableMember, MemberEvent}
import java.util.concurrent.ThreadLocalRandom
import typesafe.{WorkResult, ConnectionWorkRequest}
import typesafe.ConnectionManager.{JobFailed, SendWork}
import akka.cluster.{Cluster, MemberStatus}

import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.duration._
import typesafe.ConnectionManager.JobFailed
import akka.cluster.ClusterEvent.MemberUp
import typesafe.ConnectionManager.SendWork
import scala.Some
import typesafe.ConnectionWorkRequest
import typesafe.WorkResult
import akka.actor.RootActorPath
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.UnreachableMember

class ConnectionClient(servicePath: String, simulateWork:Boolean = false) extends Actor {

  val cluster = Cluster(context.system)

  val servicePathElements = servicePath match {
    case RelativeActorPath(elements) ⇒ elements
    case _ ⇒ throw new IllegalArgumentException(
      "servicePath [%s] is not a valid relative actor path" format servicePath)
  }

  import context.dispatcher

  val tickTask = if (simulateWork)
                    Some(context.system.scheduler.schedule(2 seconds, 2 seconds, self, "tick"))
                  else None



  var nodes = Set.empty[Address]

  var workIdCount = 0

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent])
    cluster.subscribe(self, classOf[UnreachableMember])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    tickTask.foreach(_.cancel())
  }

  def receive = {
    case SendWork(work) if nodes.isEmpty => println("No server nodes to send work request to")
    case SendWork(work) => sendWork(work)

    case "tick" if nodes.nonEmpty ⇒
      workIdCount += 1
      val work = ConnectionWorkRequest(workIdCount, "http://typesafe.com/")
      sendWork(work)

    case result: WorkResult ⇒
      println(result)
    case failed: JobFailed ⇒
      println(failed)
    case state: CurrentClusterState ⇒
      nodes = state.members.collect {
        case m if m.hasRole("compute") && m.status == MemberStatus.Up ⇒ m.address
      }
    case MemberUp(m) if m.hasRole("compute") ⇒ nodes += m.address
    case other: MemberEvent ⇒ nodes -= other.member.address
    case UnreachableMember(m) ⇒ nodes -= m.address
  }

  def sendWork(work:ConnectionWorkRequest) = {
    // just pick any one
    val nextNodeIndx: Int = ThreadLocalRandom.current.nextInt(nodes.size)
    val address = nodes.toIndexedSeq(nextNodeIndx)
    val service = context.actorSelection(RootActorPath(address) / servicePathElements)
    println(s"sending work ${work.url} to address = $address")
    service ! SendWork(work)
  }

}

object ConnectionClient {
  def props(servicePath: String, simulateWork:Boolean = false): Props = Props(classOf[ConnectionClient], servicePath, simulateWork)
}