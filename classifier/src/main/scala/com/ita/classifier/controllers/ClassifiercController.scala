package controllers

import domain.classifier.exception.ClassifierException
import models.ModelData
import org.apache.spark.{SparkConf, SparkContext}
import results.ModelExecution
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout

import scala.concurrent.ExecutionContext.Implicits.global
import utils.ClassifierDataContext
import akka.pattern.ask
import com.ita.common.mong.DAOHelpers
import com.ita.common.mong.daos.PickUpDAO
import com.ita.common.mongo.daos.mongo.DAOHelpers
import com.ita.common.utils.{ClassifierDataContext, Logger}
import com.ita.domain.Id
import domain.Model._
import mongo.DAOHelpers
import routes.ModelExecutionPayload

import scala.concurrent.Future
import scala.util.Try

trait ClassifierController extends DAOHelpers
  with Logger {

  import akka.http.scaladsl.model.StatusCodes
  import akka.http.scaladsl.server.Directives._
  import domain.Model.JFid
  implicit val dataContext: ClassifierDataContext
  //declare actors and timeout
  implicit val sparkContext: SparkContext
  import domain.classifier.exception.ClassifierException._
  import ClassifierController._
  def createModel(modelData: ModelData): Future[Id] = {
    logger.info(s"Request for $modelData arrived to controller")
    (for {
      _ <- Future(modelData._id.foreach(aId =>
        throw ClassifierException(
          classifierControllerCreateModelAlreadyCreate,
          "Error model already created!")))
      id <- Future(genObjectId())
      _ <- Future(dataContext.modelDAO.create(modelData.copy(_id = Some(id))).getOrElse(
        throw ClassifierException(
          classifierControllerCreateModelAlreadyCreate,
          "Error model already created!")))
    } yield {
      logger.info(s"${~>}Created model $modelData")
      id
    }).recover {
      case e: Throwable =>
        logger.error(s"${~>}Error when creating model $modelData", e)
        throw ClassifierException(
          classifierControllerCreateModelErrorInternalError,
          "Unknown Error when creating model",
          Some(e))
    }
  }


  def updateModel(model: ModelData): Future[Any] = Future{
    (for {
      tModel <- dataContext.modelDAO.get(model._id.getOrElse(throw ClassifierException(
        classifierControllerUpdateModelNotFound,
        "Unknown Error when updating mocel, id not definied")))
      _ <- dataContext.modelDAO.update(tModel)
    } yield {}).recover {
      case e: Throwable =>
        logger.error(s"${~>}Error when updating model $model", e)
        throw GathererException(
          classifierControllerUpdateModelInternalError,
          "Unknown Error when updating model",
          Some(e))
    }
  }

  def getPickUp(idModel: Id): Future[ModelData] = Future {
    dataContext.modelDAO.get(idModel).getOrElse( throw ClassifierException(
      classifierControllerGetModelNotFound,
      s"Pickup with id $idModel does not exist"))
  }

  def getPickUps(): Future[List[ModelData]] = Future {
    dataContext.modelDAO.getAll.toList
  }

  def getExecution(idExecution: Id): Future[ModelExecution] = Future {
    dataContext.executionDAO.get(idExecution).getOrElse( throw ClassifierException(
      classifierControllerGetExecutionNotFound,
      s"Pickup with id $idExecution does not exist"))
  }

  def getExecutions(): Future[List[ModelExecution]] = Future {
    dataContext.executionDAO.getAll.toList
  }


  def createExecution(execution: ModelExecutionPayload): Future[Id] = {
    logger.info(s"Request for $execution arrived to controller")
    (for {
      _ <- Future(execution._id.foreach(aId =>
        throw ClassifierException(
          classifierControllerCreateExecutionAlreadyCreate,
          "Error model already created!")))
      id <- Future(genObjectId())
      _ <- Future(dataContext.executionDAO.create(execution.toExecution(id).copy(_id = Some(id))).getOrElse(
        throw ClassifierException(
          classifierControllerCreateModelAlreadyCreate,
          "Error execution already created!")))
    } yield {
      logger.info(s"${~>}Created execution $execution")
      id
    }).recover {
      case e: Throwable =>
        logger.error(s"${~>}Error when execution model $execution", e)
        throw ClassifierException(
          classifierControllerCreateModelErrorInternalError,
          "Unknown Error when creating execution",
          Some(e))
    }
  }

  def trainModelExecution(executionId: Id): Future[Boolean] = {
    logger.info(s"Request for train execution $executionId arrived to controller")
    (for {
      execution <- Future(dataContext.executionDAO.get(executionId).getOrElse(
        throw ClassifierException(
          classifierControllerTrainModelExecutionDoesNotExist,
          "Error execution not created!")))
      data <- execution.getModelResult()
      _ <- Future(dataContext.executionDAO.update(execution.copy(resultModel = Some(data), status = "trained")))
    } yield {
      logger.info(s"${~>}Traines execution $execution")
      true
    }).recover {
      case e: Throwable =>
        logger.error(s"${~>}Error when training execution model $executionId", e)
        throw ClassifierException(
          classifierControllerCreateModelErrorInternalError,
          "Unknown Error when creating execution",
          Some(e))
    }
  }

  def executeModelExecution(executionId: Id): Future[Boolean] = {
    logger.info(s"Request for train execution $executionId arrived to controller")
    (for {
      execution <- Future(dataContext.executionDAO.get(executionId).getOrElse(
        throw ClassifierException(
          classifierControllerTrainModelExecutionDoesNotExist,
          "Error execution already created!")))
      data <- execution.getModelResult()
      _ <- Future(dataContext.executionDAO.update(execution.copy(resultModel = Some(data), status = "trained")))
    } yield {
      logger.info(s"${~>}Traines execution $execution")
      true
    }).recover {
      case e: Throwable =>
        logger.error(s"${~>}Error when training execution model $executionId", e)
        throw ClassifierException(
          classifierControllerCreateModelErrorInternalError,
          "Unknown Error when creating execution",
          Some(e))
    }
  }

}
object ClassifierController {
  val ~> = "[CLASSIFIER- CONTROLLER] "

  def apply()(
    implicit ndataContext: ClassifierDataContext,
    sc: SparkContext): ClassifierController =
    new ClassifierController {
      override val dataContext = ndataContext
      override implicit val sparkContext: SparkContext = sc
    }
}

