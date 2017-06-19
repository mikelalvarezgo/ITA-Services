package restApi.controllers

import actors.ActorMessages._
import actors.PickUpManager
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout
import domain.Id
import domain.gatherer.{InProcess, Ready, TweetPickUp}
import domain.gatherer.exception.GathererException
import domain.gatherer.exception.GathererException._
import mongo.daos.{DAOHelpers, PickUpDAO}

import scala.concurrent.ExecutionContext.Implicits.global
import rest.RestInterface
import utils.{GathererDataContext, Logger}
import akka.pattern.ask
import domain.Model._

import scala.concurrent.Future
import scala.util.Try

trait PickUpController extends DAOHelpers
  with Logger {

  import PickUpController.~>
  import akka.http.scaladsl.model.StatusCodes
  import akka.http.scaladsl.server.Directives._
  import domain.Model.JFid
  implicit val dataContext: GathererDataContext
  //declare actors and timeout

  val actorSystem: ActorSystem
  implicit val timeout: Timeout
  lazy val manager:ActorRef = actorSystem.actorOf(Props(PickUpManager()),"PickupManager")


  def createPickUp(tweetPickUp: TweetPickUp): Future[Id] = {
    logger.info(s"Request for $tweetPickUp arrived to controller")
    (for {
      _ <- Future(tweetPickUp._id.foreach(aId =>
        throw GathererException(
          pickUpControllerCreatePickUpAlreadyCreate,
          "Error pickup already created!")))
      id <- Future(genObjectId())
      _ <- Future(dataContext.pickupDAO.create(tweetPickUp.copy(_id = Some(id))).getOrElse(
        throw GathererException(
          pickUpControllerCreatePickUpAlreadyCreate,
          "Error pickup already created!")))
    } yield {
      logger.info(s"${~>}Created pickup $tweetPickUp")
      id
    }).recover {
      case e: Throwable =>
        logger.error(s"${~>}Error when creating pickup $tweetPickUp", e)
        throw GathererException(
          pickUpControllerCreatePickUpErrorInternalError,
          "Unknown Error when creating pickup",
          Some(e))
    }
  }

  def updatePickUp(tweetPickUp: TweetPickUp): Future[Any] = Future{
    (for {
      tPickUp <- dataContext.pickupDAO.get(tweetPickUp._id.getOrElse(throw GathererException(
        pickUpControllerUpdatePickUpNotFound,
        "Unknown Error when updating pickup, id not definied")))
      _ <- dataContext.pickupDAO.update(tweetPickUp)
    } yield {}).recover {
      case e: Throwable =>
        logger.error(s"${~>}Error when creating pickup $tweetPickUp", e)
        throw GathererException(
          pickUpControllerUpdatePickUpInternalError,
          "Unknown Error when updating pickup",
          Some(e))
    }
  }

  def getPickUp(idPickUp: Id): Future[TweetPickUp] = Future {
    dataContext.pickupDAO.get(idPickUp).getOrElse( throw GathererException(
      pickUpControllerGetPickUpNotFound,
      s"Pickup with id $idPickUp does not exist"))
  }

  def getPickUps(): Future[List[TweetPickUp]] = Future {
    dataContext.pickupDAO.getAll.toList
  }

  def startPickUp(idPickUp: Id): Future[Boolean] =  {
    (for {
      pickUp <- dataContext.pickupDAO.get(idPickUp)
      isReady <- Try(pickUp.canBeCreated)
      updateResult <- dataContext.pickupDAO.update(pickUp.copy(state= Ready))
    }yield{
      if (isReady) {
        logger.info("Starting recolection for pickup ")
        val fActResp = (manager ? StartPickUp(pickUp))
        fActResp.map {
            case WorkerStarted =>
              logger.info(s"Pickup $pickUp assigned correctly to worker")
              true
            case AllWorkersBusy =>
              logger.info(s"Pickup $pickUp assigned correctly to worker")
              false
            case _ => false
          }
      }else{
        logger.info(s"Pickup $pickUp cant not be inited")
        Future.successful(false)
      }
    }).recover {
      case e:Exception =>
        logger.error(s"Unexpected exception when starting pickup $idPickUp",e)
        Future.successful(false)
    }.get
  }

  def collectPickUp(idPickUp: Id): Future[Boolean] =  {
    (for {
      pickUp <- dataContext.pickupDAO.get(idPickUp)
      isReady <- Try(pickUp.canBeCollected)
      update <- dataContext.pickupDAO.update(pickUp.copy(state = InProcess))
    }yield{
      if (isReady) {
        logger.info(s"${~>}Collecting  for pickup ")
        val fActResp = (manager ? CollectPickUp(pickUp))
        fActResp.map {
          case WorkerStarted =>
            logger.info(s"${~>}Pickup $pickUp assigned correctly to worker")
            true
          case AllWorkersBusy =>
            logger.info(s"${~>}Pickup $pickUp assigned correctly to worker")
            false
          case _ => false
        }
      }else{
        logger.info(s"Pickup $pickUp cant not be collected")
        Future.successful(false
        )
      }
    }).recover {
      case e:Exception =>
        logger.error(s"${~>}Unexpected exception when starting pickup $idPickUp",e)
        Future.successful(false)
    }.get
  }

  def StopPickUp(idPickUp: Id): Future[Boolean] = {
    (for {
      pickUp <- dataContext.pickupDAO.get(idPickUp)
      isReady <- Try(pickUp.canBeFinished)
    }yield{
      if (isReady) {
        logger.info("Stopping recolection for pickup ")
        val fActResp = (manager ? StopPickUp(pickUp._id.get))
        fActResp.map {
          case WorkerStopped =>
            logger.info(s"Pickup $pickUp worker correctly stopped")
            true
          case WorkerNotExists =>
            logger.info(s"Pickup $pickUp does not have a worker assigned")
            false
          case _ => false
        }
      }else{
        logger.info(s"Pickup $pickUp cant not be stopped")
        Future.successful(false)
      }
    }).recover {
      case e:Exception =>
        logger.error(s"Unexpected exception when stopping pickup $idPickUp",e)
        Future.successful(false)
    }.get
  }

}
object PickUpController {
  val ~> = "[PICK-UP CONTROLLER] "

  def apply()(
    implicit ndataContext: GathererDataContext,
    actor_timeout: Timeout,
    system: ActorSystem): PickUpController =
    new PickUpController {
      override val actorSystem: ActorSystem = system
      override val timeout: Timeout = actor_timeout
      override val dataContext = ndataContext
    }
}
