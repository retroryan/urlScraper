package typesafe

import akka.actor._
import akka.routing.FromConfig


import akka.cluster.Cluster
import scala.collection.immutable.HashMap
import typesafe.ConnectionManager.SendWork
import akka.routing.ConsistentHashingRouter.ConsistentHashableEnvelope

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global

import scala.concurrent.duration._
import typesafe.ConnectionServiceSingleton.{RegisterWork, RegisterConnectionManager}
import java.util.concurrent.atomic.AtomicInteger

object ConnectionServiceSingleton {
  case object RegisterConnectionManager
  case class RegisterWork(work:ConnectionWorkRequest)
}

class ConnectionServiceSingleton extends Actor with ActorLogging {

  val cluster = Cluster(context.system)
  log.info(s"started ConnectionServiceSingleton on ${cluster.selfAddress}")

  val connectionManagerRouter = context.actorOf(Props[ConnectionManager].withRouter(FromConfig),
    name = "connectionManagerRouter")

  context.watch(connectionManagerRouter)

  //val tickTask = context.system.scheduler.schedule(1 seconds, 1 seconds, self, "tick")
  var workIdCount = new AtomicInteger()

  protected[this] var managerPathMap:HashMap[String, List[ConnectionWorkRequest]] = HashMap.empty[String, List[ConnectionWorkRequest]]

  def receive = {

     case RegisterConnectionManager => {
      log.info(s"watching actorPath = ${sender.path.toString}")
      managerPathMap = managerPathMap + (sender.path.toString -> List.empty[ConnectionWorkRequest])
      context.watch(sender)
    }

    case RegisterWork(work) => {
      //log.info(s"registered work = ${work.workId} from ${sender.path.toString}")
      val workIDs = managerPathMap.getOrElse(sender.path.toString, List.empty[ConnectionWorkRequest])
      val newWorkIDS = work :: workIDs
      managerPathMap = managerPathMap + (sender.path.toString -> newWorkIDS)
    }

    case SendWork(work) =>
      log.info(s"connection service routing work to $connectionManagerRouter")
      connectionManagerRouter ! SendWork(work)

    case "tick" =>
      val work = ConnectionWorkRequest(workIdCount.getAndIncrement, "http://typesafe.com/")
      connectionManagerRouter ! SendWork(work)

    case Terminated(actorRef) => {
      log.info(s"terminated actor = ${actorRef.path.toString}")

      managerPathMap(actorRef.path.toString).foreach {
        work =>
          log.info(s"restarting ${work.workId}")
          connectionManagerRouter ! ConsistentHashableEnvelope(SendWork(work), SendWork(work))
      }
    }
    case msg => log.info(s"UNHANDLED MESSAGE!!!   $msg")
  }
}
