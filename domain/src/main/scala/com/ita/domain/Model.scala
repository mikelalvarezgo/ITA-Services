package com.ita.domain

import spray.json.DefaultJsonProtocol.{jsonFormat2, jsonFormat9}
import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.httpx.SprayJsonSupport
import spray.json._


object Model {


   implicit val anyJF = new RootJsonFormat[Any]{ any =>

    override def write(obj: Any): JsValue = obj match {
      case v: Int => JsNumber(v)
      case v: Long => JsNumber(v)
      case v: Float => JsNumber(v)
      case v: Double => JsNumber(v)
      case v: BigDecimal => JsNumber(v)
      case v: BigInt => JsNumber(v)
      case v: List[Any@unchecked] => JsArray(v.map(any.write).toVector)
      case v: Map[String@unchecked,Any@unchecked] => JsObject(v.mapValues(any.write))
      case v => Option(v).map(s => JsString(s.toString)).getOrElse(JsNull)
    }
    override def read(json: JsValue): Any = json match {
      case JsNumber(n) => n
      case JsBoolean(b) => b
      case JsString(s) => s
      case JsArray(elements) => elements.toList.map(any.read)
      case JsObject(elements) => elements.mapValues(any.read)
      case _ => None.orNull
    }
  }

  implicit val JFid = new RootJsonFormat[Id] {
    override def write(obj: Id): JsValue =
      JsObject("$oid" -> JsString(obj.value))

    override def read(json: JsValue): Id = {
      val JsString(value) = json.asJsObject.fields("$oid")
      Id(value)
    }
  }

  implicit val JFLocation:RootJsonFormat[Location] = jsonFormat2(Location.apply)

  implicit val JFTweetInfo:RootJsonFormat[TweetInfo]  =jsonFormat11(TweetInfo.apply)


}
