package models

import client.TextProccesingApiClient
import domain.TweetInfo
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import utils.ModelToViewConverter
import utils.ModelToViewConverter._

sealed trait AggFunction {
  def toFunction:(List[Int] =>Double)
  override def toString: String = getClass.getName.init.toString.split("\\.").last.toLowerCase
}

case object MeanAgg  extends AggFunction {
  override def toFunction:(List[Int] =>Double) =
    l => (l.foldLeft(0)((a,b) => a+b))/l.size
}

object MaxAgg  extends AggFunction {
  override def toFunction:(List[Int] =>Double) =
    l => l.max
}
object Mode extends AggFunction {
  override def toFunction: (List[Int] => Double) =
    l => l.groupBy(identity).maxBy(_._2.size)._1
}

object ApiParametersNames {
  val apliClient:String = "api_client"

}
object BoostParametersNames {
  val numIter:String = "num_iterations"
  val depth:String = "depth"
  val nClassWords:String = "nClassWords"
  val strategy_boosting:String = "strategy_boosting"
}

object StandfordParametersNames {
  val numIter:String = "num_iterations"
  val depth:String = "depth"
  val nClassWords:String = "nClassWords"
  val aggFunction:String = "agg_function"
}


object ModelConverter extends ModelToViewConverterDSL {

  //import utils.ViewModelConverters._

  implicit val modelConverter =
    new ModelToViewConverter[ModelClassifier, ModelData] {
      def toView(m: ModelClassifier): ModelData = m.type_classifier.toString match {

        case v if v == API.toString =>
          val apiModel = m.asInstanceOf[ApiClassifier]
          val parameters = Map(ApiParametersNames.apliClient -> apiModel.client.toString)
          ModelData(
            Some(m._id),
            m.name,
            apiModel.listPositiveEmojis,
            apiModel.listNegativeEmojis,
            API,
            parameters,
            apiModel.setPartition.get)

        case v if v == BOOSTING.toString =>
          val boostModel = m.asInstanceOf[GradientBoostingClassifier]
          val parameters = Map(
            BoostParametersNames.depth -> boostModel.depth.toString,
            BoostParametersNames.numIter -> boostModel.num_iterations.toString,
            BoostParametersNames.nClassWords -> boostModel.nClassWords.toString)

          ModelData(
            Some(boostModel._id),
            boostModel.name,
            boostModel.listPositiveEmojis,
            boostModel.listNegativeEmojis,
            BOOSTING,
            parameters,
            boostModel.setPartition.get)

        case v if v == NLP.toString =>
          val nlpModel = m.asInstanceOf[NLPStanfordClassifier]
          val parameters = Map(
            StandfordParametersNames.aggFunction -> nlpModel.agregation_function_name.toString,
            BoostParametersNames.numIter -> nlpModel.num_iterations.toString)

          ModelData(
            Some(nlpModel._id),
            nlpModel.name,
            nlpModel.listPositiveEmojis,
            nlpModel.listNegativeEmojis,
            NLP,
            parameters,
            nlpModel.setPartition.get)
      }

      def toModel(v: ModelData): ModelClassifier = v.type_classifier.toString match {
        case l if l == API.toString =>
          ApiClassifier(
            v._id.get,
            v.name,
            TextProccesingApiClient("api_client"),
            Some(v.partitionConf),
            v.listPositiveEmojis,
            v.listNegativeEmojis)

        case l if l == BOOSTING.toString =>
          GradientBoostingClassifier(
            v._id.get,
            v.name,
            v.parameters(BoostParametersNames.numIter).toInt,
            Some(v.partitionConf),
            v.parameters(BoostParametersNames.depth).toInt,
            v.parameters(BoostParametersNames.strategy_boosting),
            v.listPositiveEmojis,
            v.listNegativeEmojis,
            v.parameters(BoostParametersNames.nClassWords).toInt)

        case l if l == NLP.toString =>

          val aggFunction: AggFunction = v.parameters(StandfordParametersNames.aggFunction) match {
            case t if t == MeanAgg.toString => MeanAgg
            case t if t == MaxAgg.toString => MaxAgg
            case t if t == Mode.toString => Mode
          }
          NLPStanfordClassifier(
            v._id.get,
            v.name,
            v.parameters(StandfordParametersNames.numIter).toInt,
            aggFunction,
            aggFunction.toFunction,
            v.listPositiveEmojis,
            v.listNegativeEmojis,
            Some(v.partitionConf))
      }
    }
}