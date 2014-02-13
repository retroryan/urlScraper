package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json.{JsSuccess, Json}
import scala.concurrent.Future


case class Message(value: String)


object UrlController extends Controller {

  implicit val fooWrites = Json.writes[Message]

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def getMessage = Action {
    Ok(Json.toJson(Message("Hello from Whatever!")))
  }

  def sendMessage = Action.async(parse.json) {
    request =>
      val incomingJson = request.body
      val optScrapeUrl = (incomingJson \ "scrapeUrl").validate[String]

      val result = optScrapeUrl match {
        case JsSuccess(scrapeUrl, path) =>
          Logger.debug(s"received scrapeUrl=$scrapeUrl")
          Ok(Json.toJson(Message(s"Added URL $scrapeUrl")))
        case _ => BadRequest("Invalid JSON")
      }

      Future.successful(result)
  }

  def javascriptRoutes = Action {
    implicit request =>
      Ok(Routes.javascriptRouter("jsRoutes")(
        routes.javascript.UrlController.getMessage,
        routes.javascript.UrlController.sendMessage
      )).as(JAVASCRIPT)
  }

}