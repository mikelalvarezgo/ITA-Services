package restApi.routes

import akka.actor.Actor
import domain.Id
import domain.gatherer.TweetPickUp
import domain.gatherer.exception.GathererException
import restApi.controllers.PickUpController
import utils.Logger
import domain.Model._
import domain.gatherer.Model._
import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport._
import spray.routing.{Directives, HttpService, Route, StandardRoute}

import scala.util.Try
import scala.concurrent.ExecutionContext.Implicits.global

class ApiActor(
  apiName: String,
  pickUpController: PickUpController) extends Actor
  with HttpService
  with Logger {
  def actorRefFactory = context
  val pickupRoutes:Route =
     pathPrefix("topic") {
      post {
        entity(as[TweetPickUp]) { tweetPickUp: TweetPickUp =>
          complete(pickUpController.createPickUp(tweetPickUp))
        }
      }
      put {
        entity(as[TweetPickUp]) { tweetPickUp: TweetPickUp =>
          complete(pickUpController.updatePickUp(tweetPickUp))
        }
      }
      get {
        entity(as[Id]) { id: Id =>
          complete(pickUpController.getPickUp(id))
        }
      }
      pathPrefix("stop") {
        post {
          entity(as[Id]) { id: Id =>
            complete(pickUpController.StopPickUp(id))
          }
        }
      }
      pathPrefix("start") {
        post {
          entity(as[Id]) { id: Id =>
            complete(pickUpController.startPickUp(id))
          }
        }
      }
    }

  def receive = runRoute(pickupRoutes)

  def pickUpCreate(pickup: TweetPickUp): StandardRoute =
     Try {
       pickUpController.createPickUp(pickup)
     }.map(response => complete(response)).recover {
       case gathEx: GathererException =>
         complete(StatusCodes.InternalServerError, gathEx)
       case ex: Throwable =>
         logger.error(s"Unexpected error when creating pickup $pickup'", ex)
         complete(StatusCodes.InternalServerError, GathererException(
           GathererException.pickUpControllerCreatePickUpErrorInternalError,
           s"Unexpected error when creating action $pickup"))
     }.get

  def getPickUp(idPickUp: Id): StandardRoute =
    Try {
      pickUpController.getPickUp(idPickUp)
    }.map(response => complete(response)).recover {
      case gathEx: GathererException =>
        complete(StatusCodes.InternalServerError, gathEx)
      case ex: Throwable =>
        logger.error(s"Unexpected error when getting pickup $idPickUp'", ex)
        complete(StatusCodes.InternalServerError, GathererException(
          GathererException.pickUpControllerCreatePickUpErrorInternalError,
          s"Unexpected error when getting action $idPickUp"))
    }.get
  def pickUpUpdate(pickup: TweetPickUp): StandardRoute =
    Try {
      pickUpController.updatePickUp(pickup)
    }.map(response => complete(response)).recover {
      case gathEx: GathererException =>
        complete(StatusCodes.InternalServerError, gathEx)
      case ex: Throwable =>
        logger.error(s"Unexpected error when updating pickup $pickup'", ex)
        complete(StatusCodes.InternalServerError, GathererException(
          GathererException.pickUpControllerUpdatePickUpInternalError,
          s"Unexpected error when updating action $pickup"))
    }.get

}
