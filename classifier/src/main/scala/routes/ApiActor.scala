package routes


import controllers.ClassifierController
import spray.routing.{Route, _}
import akka.actor.Actor
import akka.util.Timeout
import domain.Id
import utils.Logger
import domain.Model._

import scala.concurrent.duration._
import classifier.Model._
import spray.httpx.SprayJsonSupport._
import spray.routing._
import models.ModelData
import routes.ApiHelper._
import scala.concurrent.ExecutionContext.Implicits.global
import domain.Model.JFid
import results.ModelExecution
import spray.json.{JsBoolean, JsValue, RootJsonFormat}
import spray.json.RootJsonFormat

class ApiActor(
  apiName: String,
  classifierController: ClassifierController) extends Actor
  with HttpService
  with Logger {
  def actorRefFactory = context
  implicit  val BooleanJF = new RootJsonFormat[Boolean] {
    override def write(obj:Boolean):JsValue = JsBoolean(obj)
    override def read(json: JsValue):Boolean =  json match  {
      case JsBoolean(v) => v
      case _ => ???
    }
  }
  implicit val timeout = Timeout(10 seconds)
  val modelRoutes: Route =
    path("model"){
      get {
        entity(as[Id]) { id: Id =>
          complete(classifierController.getPickUp(id))
        }
      }~
        put {
          entity(as[ModelData]) { model: ModelData =>
            complete(classifierController.updateModel(model))
          }
        }~
        post {
          entity(as[ModelData]) { payload: ModelData =>
            complete(classifierController.createModel(payload))
          }
        }
    } ~
      path("execution") {
        get {
          entity(as[Id]) { id: Id =>
            complete(classifierController.getExecution(id))
          }
        }~
          post {
            entity(as[ModelExecutionPayload]) { payload: ModelExecutionPayload =>
              complete(classifierController.createExecution(payload))
            }
          }
      } ~
      path("train"){
        post {
          entity(as[Id]) { id: Id =>
            complete(classifierController.trainModelExecution(id))
          }
        }
      }~
      path("execute") {
        post {
          entity(as[Id]) { id: Id =>
            complete(classifierController.executeModelExecution(id))
          }
        }
      }
  def receive = runRoute(modelRoutes)
}
