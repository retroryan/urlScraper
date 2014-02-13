package typesafe

import com.typesafe.config.ConfigFactory
import akka.actor._

import akka.contrib.pattern.ClusterSingletonManager
import akka.cluster.{MemberStatus, Cluster}
import scala.Some
import akka.cluster.ClusterEvent.{MemberUp, CurrentClusterState, UnreachableMember, MemberEvent}

import typesafe.ConnectionManager.{JobFailed, SendWork}
import actors.ConnectionClient

object RunConnectionServers {

  def startConnectionServers(implicit system: ActorSystem) = {
    var cluster = Cluster(system)

    if (cluster.selfRoles.exists(r => r.startsWith("compute"))) {
      //#create singleton connectionService
      system.actorOf(ClusterSingletonManager.props(
        singletonProps = _ ⇒ Props[ConnectionServiceSingleton], singletonName = "connectionService",
        terminationMessage = PoisonPill, role = Some("compute")),
        name = "singleton")
    }

    //#create cluster manager
    system.actorOf(Props[ClusterManager], name = "clusterManager")
  }

  def main(args: Array[String]): Unit = {

    val initialConfig = if (args.nonEmpty) ConfigFactory.parseString(s"akka.remote.netty.tcp.port=${args(0)}")
    else ConfigFactory.empty

    val config = initialConfig.withFallback(
      ConfigFactory.parseString("akka.cluster.roles = [compute]")).
      withFallback(ConfigFactory.load())

    val roles = config.getList("akka.cluster.roles")
    val port = config.getString("akka.remote.netty.tcp.port")

    println(s"RunConnectionServers roles = ${roles.toString} and port = $port")

    //Actor System has to be application so that it clusters with the Play Server
    implicit val system = ActorSystem("application", config)
    startConnectionServers(system)


  }
}


object RunConnectionClients {

  def main(args: Array[String]): Unit = {

    val system = ActorSystem("application")
    val cluster = Cluster(system)
    val roles = cluster.getSelfRoles
    val port = cluster.selfAddress

    println(s"RunConnectionClients roles = ${roles.toString} and selfAddress = $port")

    //set simulateWork to true to run a test of the client
    val simulateWork = true
    system.actorOf(ConnectionClient.props("/user/clusterManager", simulateWork), "client")
  }
}


