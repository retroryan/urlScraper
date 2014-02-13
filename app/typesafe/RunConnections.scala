package typesafe

import com.typesafe.config.ConfigFactory
import akka.actor._

import akka.contrib.pattern.ClusterSingletonManager
import akka.cluster.{MemberStatus, Cluster}
import scala.Some
import akka.cluster.ClusterEvent.{MemberUp, CurrentClusterState, UnreachableMember, MemberEvent}
import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.duration._
import typesafe.ConnectionManager.{JobFailed, SendWork}

object RunConnectionServers {

  def main(args: Array[String]): Unit = {

    val initialConfig = if (args.nonEmpty) ConfigFactory.parseString(s"akka.remote.netty.tcp.port=${args(0)}")
                        else ConfigFactory.empty

    val config = initialConfig.withFallback(
          ConfigFactory.parseString("akka.cluster.roles = [compute]")).
                withFallback(ConfigFactory.load())

    val roles = config.getList("akka.cluster.roles")
    val port = config.getString("akka.remote.netty.tcp.port")

    println(s"RunConnectionServers roles = ${roles.toString} and port = $port")

    val system = ActorSystem("ClusterSystem", config)

    //#create singleton connectionService
    system.actorOf(ClusterSingletonManager.props(
      singletonProps = _ ⇒ Props[ConnectionServiceSingleton], singletonName = "connectionService",
      terminationMessage = PoisonPill, role = Some("compute")),
      name = "singleton")

    //#create cluster manager
    system.actorOf(Props[ClusterManager], name = "clusterManager")
  }
}


object RunConnectionClients {

  def main(args: Array[String]): Unit = {

    val system = ActorSystem("ClusterSystem")
    val cluster = Cluster(system)
    val roles = cluster.getSelfRoles
    val port = cluster.selfAddress

    println(s"RunConnectionClients roles = ${roles.toString} and selfAddress = $port")

    system.actorOf(Props(classOf[ConnectionSampleClient], "/user/clusterManager"), "client")
  }
}


class ConnectionSampleClient(servicePath: String) extends Actor {

  val cluster = Cluster(context.system)

  val servicePathElements = servicePath match {
    case RelativeActorPath(elements) ⇒ elements
    case _ ⇒ throw new IllegalArgumentException(
      "servicePath [%s] is not a valid relative actor path" format servicePath)
  }

  import context.dispatcher

  val tickTask = context.system.scheduler.schedule(2 seconds, 2 seconds, self, "tick")
  var nodes = Set.empty[Address]

  var n = 0

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent])
    cluster.subscribe(self, classOf[UnreachableMember])
  }
  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    tickTask.cancel()
  }

  def receive = {
    case "tick" if nodes.nonEmpty ⇒
      // just pick any one
      val nextNodeIndx: Int = ThreadLocalRandom.current.nextInt(nodes.size)
      val address = nodes.toIndexedSeq(nextNodeIndx)
      val service = context.actorSelection(RootActorPath(address) / servicePathElements)
      n += 1
      val work = Work(n, "Some Job")
      println(s"sending work ${work.workId} to address = $address and service = $service and nextNodeIndx=$nextNodeIndx")
      service ! SendWork(work)
    case result: WorkResult ⇒
      println(result)
    case failed: JobFailed ⇒
      println(failed)
    case state: CurrentClusterState ⇒
      nodes = state.members.collect {
        case m if m.hasRole("compute") && m.status == MemberStatus.Up ⇒ m.address
      }
    case MemberUp(m) if m.hasRole("compute") ⇒ nodes += m.address
    case other: MemberEvent                  ⇒ nodes -= other.member.address
    case UnreachableMember(m)                ⇒ nodes -= m.address
  }

}