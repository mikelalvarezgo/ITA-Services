package com.ita.classifier.routes

import spray.routing.{Route, _}
import akka.actor.Actor
import akka.util.Timeout
import com.ita.classifier.controllers.ClassifierController
import com.ita.domain.Model._

import scala.concurrent.duration._
import com.ita.domain.Id
import spray.httpx.SprayJsonSupport._
import spray.routing._
import com.ita.classifier.models.ModelData
import com.ita.classifier.routes.ApiHelper._

import scala.concurrent.ExecutionContext.Implicits.global
import com.ita.domain.Model.JFid
import com.ita.classifier.results.ModelExecution
import com.ita.domain.utils.Logger
import spray.json.{JsBoolean, JsValue, RootJsonFormat}
import spray.json.RootJsonFormat
import com.ita.classifier.Model._
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
      path("evaluate"){
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
