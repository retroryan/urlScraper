package typesafe

import akka.actor.{Props, ActorLogging, Actor}
import akka.routing.FromConfig
import typesafe.ConnectionManager.SendWork
import akka.routing.ConsistentHashingRouter.ConsistentHashableEnvelope

class ConnectionServiceSingleton extends Actor with ActorLogging{

  val connectionManagerRouter = context.actorOf(Props[ConnectionManager].withRouter(FromConfig),
    name = "connectionManagerRouter")

  override def receive: Actor.Receive = {
    case SendWork(work) =>
      println(s"connection service routing work to $connectionManagerRouter")
      connectionManagerRouter ! ConsistentHashableEnvelope(SendWork(work), SendWork(work))

  }
}
