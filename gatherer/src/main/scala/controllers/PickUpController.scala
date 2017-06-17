package controllers

import actors.PickUpManager
import domain.Id
import domain.gatherer.TweetPickUp
import domain.gatherer.exception.GathererException
import domain.gatherer.exception.GathererException._
import mongo.daos.{DAOHelpers, PickUpDAO}

import scala.concurrent.ExecutionContext.Implicits.global
import rest.RestInterface
import utils.Logger

import scala.concurrent.Future
import scala.util.Try

trait PickUpController extends DAOHelpers
  with Logger {

  import PickUpController.~>

  implicit val pickUpDao: PickUpDAO
  //declare actors and timeout
  val manager:PickUpManager
  def createPickUp(tweetPickUp: TweetPickUp): Future[Id] = {
    (for {
      _ <- Future(tweetPickUp._id.foreach(aId =>
        throw GathererException(
          pickUpControllerCreatePickUpAlreadyCreate,
          "Error pickup already created!")))
      id <- Future(genObjectId())
      _ <- Future(pickUpDao.create(tweetPickUp.copy(_id = Some(id))).getOrElse(
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
      tPickUp <- pickUpDao.get(tweetPickUp._id.getOrElse(throw GathererException(
        pickUpControllerUpdatePickUpNotFound,
        "Unknown Error when updating pickup, id not definied")))
      _ <- pickUpDao.update(tweetPickUp)
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
    pickUpDao.get(idPickUp).getOrElse( throw GathererException(
      pickUpControllerGetPickUpNotFound,
      s"Pickup with id $idPickUp does not exist"))
  }

  def getPickUps(): Future[List[TweetPickUp]] = Future {
    pickUpDao.getAll
  }

  def startPickUp(idPickUp: Id): Future[Any] = ???

  def StopPickUp(idPickUp: Id): Future[Any] = ???

}

object PickUpController {
  val ~> = "[PICK-UP CONTROLLER] "

  def apply()(nPickUpDao: PickUpDAO): PickUpController =
    new PickUpController {
      override val pickUpDao = nPickUpDao
    }
}
