package actors

import play.api._
import play.api.libs.concurrent.Akka
import akka.cluster.Cluster
import java.net.URL
import akka.actor.IOManager.Settings
import typesafe.RunConnectionServers
import akka.actor.Props

/**
 * Lookup for actors used by the web front end.
 */
object Actors {

  private def actors(implicit app: Application) = app.plugin[Actors]
    .getOrElse(sys.error("Actors plugin not registered"))

  /**
   * Get the connection client.
   */
  def connectionClient(implicit app: Application) = actors.connectionClient
}

/**
 * Manages the creation of actors in the web front end.
 *
 * This is discovered by Play in the `play.plugins` file.
 */
class Actors(app: Application) extends Plugin {

  private def system = Akka.system(app)

  override def onStart() = {
    RunConnectionServers.startConnectionServers(system)
  }

  private lazy val connectionClient = system.actorOf(ConnectionClient.props("/user/clusterManager"), "client")
}
