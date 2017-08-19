package models

import com.lightbend.emoji.Emoji
import domain.{Id, TweetInfo}
import org.apache.commons.collections.map.IdentityMap
import org.apache.spark.rdd.RDD
import results.ModelResult

/**
  * Created by mikelwyred on 19/08/2017.
  */
case class PartitionConf(
  train: Double,
  validation:Double,
  classification:Double)

  case class SetPartition(
    trainingSet: RDD[TweetInfo],
    validationSet: RDD[TweetInfo],
    classifySet: RDD[TweetInfo])

sealed trait ModelClassifier{

  val _id:Id
  val name:String
  val type_classifier: String
  val dataSet: RDD[TweetInfo]
  val setPartition:  Option[PartitionConf]


  def runModel:ModelResult

  def prepareModel:SetPartition

}


case class GradientBoostingClassifier
(_id: Id,
  name: String,
  num_iterations: Int,
  dataSet:RDD[TweetInfo],
  setPartition: Option[PartitionConf],
  depth: Int,
  strategy: String,
  listPositiveEmojis: List[Emoji],
  listNegativeEmojis: List[Emoji],
  override val type_classifier:String = "gradient_boosting")
  extends ModelClassifier {

  def runModel:ModelResult = ???

  def prepareModel: ModelResult = ???

  def filterTweetsWithEmojis:RDD[TweetInfo] = {
    dataSet.filter(tweet => tweet.contain_emoji)
  }


}

case class ApiClassifier(
  _id: Id,
  name: String,
  num_iterations: Int,
  dataSet:RDD[TweetInfo],
  setPartition: Option[PartitionConf])



