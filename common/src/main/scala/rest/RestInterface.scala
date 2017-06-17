package rest

import akka.actor._
import akka.util.Timeout
import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport._
import spray.routing._
import utils.Logger

import scala.concurrent.duration._
import scala.language.postfixOps

class RestInterface extends HttpServiceActor
  with RestApi {

  def receive = runRoute(routes)
}

trait RestApi extends HttpService with Logger { actor: Actor =>

  implicit val timeout = Timeout(10 seconds)

  def routes: Route = ???
}

abstract  class Responder(requestContext:RequestContext) extends Actor with ActorLogging {

  def receive = ???

  private def killYourself = self ! PoisonPill

}
