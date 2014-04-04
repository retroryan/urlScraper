package typesafe

import akka.actor._
import akka.cluster.{Member, Cluster}
import scala.collection.immutable
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberEvent}
import typesafe.ConnectionManager.JobFailed
import typesafe.Messages.{RestartConnectionWorkSet, AckSendWork}
import scala.collection.immutable.HashSet
import akka.actor.RootActorPath
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberRemoved
import typesafe.ConnectionManager.JobFailed
import akka.cluster.ClusterEvent.MemberUp
import typesafe.ConnectionManager.SendWork
import typesafe.ClusterManagerWatcher.{GetConnectionWorkSet, UnwatchWork, AddWorkWatch}


class ClusterManager extends Actor with ActorLogging {


  val cluster = Cluster(context.system)

  println(s"cluster manager running on ${cluster.selfAddress}")

  // sort by age, oldest first
  val ageOrdering = Ordering.fromLessThan[Member] {
    (a, b) ⇒ a.isOlderThan(b)
  }
  var membersByAge: immutable.SortedSet[Member] = immutable.SortedSet.empty(ageOrdering)


  // subscribe to cluster changes
  // re-subscribe when restart
  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberEvent])

  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case work: SendWork if membersByAge.isEmpty ⇒
      sender ! JobFailed("Service unavailable, try again later")
    case job: SendWork ⇒
      println(s"ClusterManager sending work ${job.work.workId} to master = $currentMaster")
      currentMaster.tell(job, sender)

    case ackSendWork@AckSendWork(_, _) => {

      println(s"adding watch ${ackSendWork.nodeAddress}")

      context.child(ackSendWork.nodeAddress.toString) match {
        case Some(clusterManagerWatcher) =>
          println(s"adding watch ${clusterManagerWatcher.hashCode()}")
          clusterManagerWatcher ! AddWorkWatch(ackSendWork.work)
        case None => log.debug("ERROR - ackSendWork.nodeAddress.toString doesn't exist!")
      }

      currentMaster.tell(ackSendWork, sender)
    }

    case state: CurrentClusterState ⇒
      membersByAge = immutable.SortedSet.empty(ageOrdering) ++ state.members.collect {
        case m if m.hasRole("compute") => m
      }
    case MemberUp(member) ⇒
      if (member.hasRole("compute")) {
        membersByAge += member

        val memberUid = member.hashCode().toString
        log.debug(s"memberUid = ${member.hashCode().toString}")
        context.child(memberUid).getOrElse {
          context.actorOf(Props(new ClusterManagerWatcher(member.address)), member.hashCode().toString)
        }

      }
      println(s"MemberUp cluster now has ${membersByAge.size} nodes")

    case MemberRemoved(member, _) ⇒
      if (member.hasRole("compute")) {
        membersByAge -= member
        context.child(member.hashCode().toString).foreach(clusterManagerWatcher => {
          println(s"rebalancing ${member.hashCode().toString}")
          clusterManagerWatcher ! GetConnectionWorkSet
          context.stop(clusterManagerWatcher)
        })
      }
      println(s"MemberRemoved cluster now has ${membersByAge.size} nodes")

    case RestartConnectionWorkSet(connectionWorkSet) => currentMaster ! RestartConnectionWorkSet(connectionWorkSet)

    case _: MemberEvent => println("not interesting")
  }

  def currentMaster: ActorSelection =
    context.actorSelection(RootActorPath(membersByAge.head.address) /
      "user" / "singleton" / "connectionService")
}


class ClusterManagerWatcher(address: Address) extends Actor {

  protected[this] var connectionWorkSet: HashSet[ConnectionWorkRequest] = HashSet.empty[ConnectionWorkRequest]

  def receive = {
    case GetConnectionWorkSet => sender ! RestartConnectionWorkSet(connectionWorkSet)
    case AddWorkWatch(work) =>
      println(s"added ${work.workId} to watch on node $address")
      connectionWorkSet = connectionWorkSet + work
    case UnwatchWork(work) =>
      connectionWorkSet = connectionWorkSet - work
  }
}

object ClusterManagerWatcher {

  case object GetConnectionWorkSet

  case class AddWorkWatch(work: ConnectionWorkRequest)

  case class UnwatchWork(work: ConnectionWorkRequest)

}
