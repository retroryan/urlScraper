package typesafe

import akka.actor.{Props, ActorLogging, Actor}
import akka.routing.FromConfig
import typesafe.ConnectionManager.SendWork
import akka.routing.ConsistentHashingRouter.ConsistentHashableEnvelope

import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global
import typesafe.Messages.{RestartConnectionWorkSet, AckSendWork}


class ConnectionServiceSingleton extends Actor with ActorLogging{

  val connectionManagerRouter = context.actorOf(Props[ConnectionManager].withRouter(FromConfig),
    name = "connectionManagerRouter")

  val tickTask = context.system.scheduler.schedule(2 seconds, 5 seconds, self, "tick")

  var workIdCount = 0

  override def receive: Actor.Receive = {

    case SendWork(work) =>
      println(s"connection service routing work to $connectionManagerRouter")
      connectionManagerRouter ! ConsistentHashableEnvelope(SendWork(work), SendWork(work))

    case "tick" =>
      val work = ConnectionWorkRequest(workIdCount, "http://typesafe.com/")
      connectionManagerRouter ! ConsistentHashableEnvelope(SendWork(work), SendWork(work))

    case ackSendWork@AckSendWork(work, _) =>
       println(s"ConnectionServiceSingleton AckSendWork from ${sender.path}")

    case RestartConnectionWorkSet(connectionWorkSet) => {
      println(s"restarting ${connectionWorkSet.size} work items")
      connectionWorkSet.foreach(work => {
        connectionManagerRouter ! ConsistentHashableEnvelope(SendWork(work), SendWork(work))
      })
    }

  }
}
