package typesafe

import akka.actor.{RootActorPath, ActorSelection, ActorLogging, Actor}
import akka.cluster.{Member, Cluster}
import scala.collection.immutable
import akka.cluster.ClusterEvent.{MemberRemoved, MemberUp, CurrentClusterState, MemberEvent}
import typesafe.ConnectionManager.{JobFailed, SendWork}


class ClusterManager extends Actor with ActorLogging {

  import context.dispatcher
  val cluster = Cluster(context.system)

  // sort by age, oldest first
  val ageOrdering = Ordering.fromLessThan[Member] { (a, b) ⇒ a.isOlderThan(b) }
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
    case state: CurrentClusterState ⇒
      membersByAge = immutable.SortedSet.empty(ageOrdering) ++ state.members.collect {
        case m if m.hasRole("compute") ⇒ m
      }
    case MemberUp(m)         ⇒
      if (m.hasRole("compute")) membersByAge += m
      println(s"MemberUp cluster now has ${membersByAge.size} nodes")
    case MemberRemoved(m, _) ⇒
      if (m.hasRole("compute")) membersByAge -= m
      println(s"MemberRemoved cluster now has ${membersByAge.size} nodes")
    case _: MemberEvent      ⇒ // not interesting
  }

  def currentMaster: ActorSelection =
    context.actorSelection(RootActorPath(membersByAge.head.address) /
      "user" / "singleton" / "connectionService")
}
