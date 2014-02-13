package typesafe

import akka.actor.{Props, ActorLogging, Actor}
import scala.concurrent.forkjoin.ThreadLocalRandom
import java.util.UUID

import scala.concurrent.duration._
import typesafe.ConnectionManager.SendWork
import akka.cluster.Cluster

import play.api.libs.ws.WS

class ConnectionWorker extends Actor with ActorLogging{

  import typesafe.ConnectionWorker._

  import context.dispatcher

  def scheduler = context.system.scheduler
  def rnd = ThreadLocalRandom.current
  def nextWorkId(): String = UUID.randomUUID().toString

  val cluster = Cluster(context.system)

  var n = 0

  override def receive: Actor.Receive = {
    case SendWork(work) =>
      println(s"received work ${work.workId} and my address is ${context.self.path.address}")
      scheduler.schedule(0.seconds, 5.seconds, self, ConnectionWorker.Tick(work))
    case Tick(work) =>
      n += 1
      log.info(s"worked on ${work.workId} and produced $n}")
  }

  def scrapeUrl(url:String) = {
    WS.url(url).get
  }

}

object ConnectionWorker {

  case class Tick(work:Work)


  def props(): Props =
    Props(classOf[ConnectionWorker])

}
