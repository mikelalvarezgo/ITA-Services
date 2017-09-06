package com.ita.gatherer.utils



case class TweetsFilter(
  name:String,
  lenguage: Option[String]){

  def isTweetLenguage(tweet: String): Boolean ={
    if(lenguage.isDefined) {
      true
    }
    else false
  }
}

object LenguageHelper {
  trait Lenguage {
    override def  toString = getClass.getName.split("\\$").last.toLowerCase
  }
  case object ES extends Lenguage
  case object EN extends Lenguage
  case object FR extends Lenguage
}
