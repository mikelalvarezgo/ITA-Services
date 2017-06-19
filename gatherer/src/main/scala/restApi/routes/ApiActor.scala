package restApi.routes

import akka.actor.Actor
import akka.util.Timeout
import domain.Id
import domain.gatherer.{Ready, TweetPickUp}
import domain.gatherer.exception.GathererException
import restApi.controllers.PickUpController
import utils.Logger
import domain.Model._
import domain.gatherer.Model._
import spray.http.{MediaTypes, StatusCodes}
import spray.httpx.SprayJsonSupport._
import spray.routing._
import restApi.routes.ApiHelper._
import spray.httpx.unmarshalling.Unmarshaller

import scala.concurrent.duration._
import scala.util.Try
import scala.concurrent.ExecutionContext.Implicits.global

class ApiActor(
  apiName: String,
  pickUpController: PickUpController) extends Actor
  with HttpService
  with Logger {
  def actorRefFactory = context

  implicit val timeout = Timeout(10 seconds)
  val pickupRoutes: Route =
    path("topic") {
      get {
        entity(as[Id]) { id: Id =>
          complete(pickUpController.getPickUp(id))
        }
      }~
      put {
        entity(as[TweetPickUp]) { tweetPickUp: TweetPickUp =>
          complete(pickUpController.updatePickUp(tweetPickUp))
        }
      }~
      post {
        entity(as[CreatePickUpPayloadRequest]) { payload: CreatePickUpPayloadRequest =>
          complete(pickUpController.createPickUp(payload.converToTweetPickUp))
        }
      }
    } ~
      path("stop" / Segment: PathMatcher1[String]) { idPickUp =>
        post {
          complete(pickUpController.StopPickUp(Id(idPickUp)))
        }
      }~
      path("start" / Segment: PathMatcher1[String]) { idPickUp =>
        post {
          complete(pickUpController.startPickUp(Id(idPickUp)))
        }
      }~
      path("collect" / Segment: PathMatcher1[String]) { idPickUp =>
       post {
         complete(pickUpController.collectPickUp(Id(idPickUp)))
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
