package actors

import akka.actor.Actor
import actors.Echo.{Response, EchoMsg}
import scala.concurrent.Future

object Echo {
  case class EchoMsg(msg:String)
  case class Response(msg:String)
}


class Echo extends Actor {

  override def receive = {

    case EchoMsg(data) =>
      val orgSender = sender
      Future {
        val result = transform(data)
        orgSender ! Response(result)
      }

  }

  def transform(data:String) = {
    data.reverse
  }
}
