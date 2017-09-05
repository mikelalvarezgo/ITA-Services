package models

/**
  * Created by mikelwyred on 26/08/2017.
  */
sealed abstract class ModelType{
  override def toString = getClass.getName.init.toString.split("\\.").last.toLowerCase

}
case object NLP extends ModelType
case object BOOSTING extends ModelType
case object API extends ModelType
case object BAYES extends ModelType
case object RR extends ModelType
case object RNN extends ModelType


