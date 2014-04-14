package typesafe

import akka.actor._
import akka.cluster.{Member, Cluster}
import scala.collection.immutable
import akka.cluster.ClusterEvent.MemberEvent

import akka.actor.RootActorPath
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberRemoved
import akka.cluster.ClusterEvent.MemberUp


class ClusterSingletonProxy extends Actor with ActorLogging {

  val cluster = Cluster(context.system)
  println(s"started ClusterSingletonProxy on ${cluster.selfAddress}")

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

    case state: CurrentClusterState ⇒
      membersByAge = immutable.SortedSet.empty(ageOrdering) ++ state.members.collect {
        case m if m.hasRole("compute") => m
      }
    case MemberUp(member) ⇒
      if (member.hasRole("compute")) {
        membersByAge += member
      }
      println(s"MemberUp cluster now has ${membersByAge.size} nodes")

    case MemberRemoved(member, _) ⇒
      if (member.hasRole("compute")) {
        membersByAge -= member
      }
      println(s"MemberRemoved cluster now has ${membersByAge.size} nodes")

    case msg: Any if membersByAge.isEmpty => log.error("No singleton available.")

    case msg: Any =>
      val rootPath = RootActorPath(membersByAge.head.address)
      log.info(s"forwarding message ${msg}  to ${rootPath}")
      currentMaster.tell(msg, sender)

  }

  def currentMaster: ActorSelection =
    context.actorSelection(RootActorPath(membersByAge.head.address) /
      "user" / "singleton" / "connectionService")
}



