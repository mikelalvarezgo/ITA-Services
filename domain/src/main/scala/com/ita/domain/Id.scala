package com.ita.domain

import org.bson.types.ObjectId

import scala.language.implicitConversions

case class Id(value: String)

object Id {

  def generate: Id = Id(ObjectId.get().toHexString)

  implicit def toId(value: String): Id = Id(value)

  implicit def toValue(id: Id): String = id.value
}
