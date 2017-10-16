package com.ita.classifier.models

import com.ita.classifier.client.TextProccesingApiClient
import com.ita.classifier.utils.ModelToViewConverter
import com.ita.classifier.utils.ModelToViewConverter.ModelToViewConverterDSL

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

object BayesParametersNames {
  val nClassWords:String = "nClassWords"
  val lambda:String = "lambda"
  val type_model:String = "type_model"
}

object StandfordParametersNames {
  val numIter:String = "num_iterations"
  val depth:String = "depth"
  val nClassWords:String = "nClassWords"
  val aggFunction:String = "agg_function"
}

object  RandomForestParametersNames {

  val num_trees: String = "num_trees"
  val depth:String = "depth"
  val nClassWords:String = "nClassWords"
  val max_bins:String = "max_bins"
  val impurity:String = "impurity"
  val seed:String = "seed"

  val subset_strategy:String = "subset_strategy"
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
            apiModel.tag_tweets,
            API,
            parameters,
            apiModel.setPartition)

        case v if v == BOOSTING.toString =>
          val boostModel = m.asInstanceOf[GradientBoostingClassifier]
          val parameters = Map(
            BoostParametersNames.depth -> boostModel.depth.toString,
            BoostParametersNames.numIter -> boostModel.num_iterations.toString,
            BoostParametersNames.nClassWords -> boostModel.nClassWords.toString)

          ModelData(
            Some(boostModel._id),
            boostModel.name,
            boostModel.tag_tweets,
            BOOSTING,
            parameters,
            boostModel.setPartition)


        case v if v == BAYES.toString =>
          val boostModel = m.asInstanceOf[BayesClassifier]
          val parameters = Map(
            BayesParametersNames.type_model -> boostModel.type_model,
            BayesParametersNames.lambda -> boostModel.lambda.toString,
            BoostParametersNames.nClassWords -> boostModel.nClassWords.toString)

          ModelData(
            Some(boostModel._id),
            boostModel.name,
            boostModel.tag_tweets,
            BOOSTING,
            parameters,
            boostModel.setPartition)
        case v if v == RR.toString =>
          val nlpModel = m.asInstanceOf[RandomForestClassifier]
          val parameters = Map(
            RandomForestParametersNames.depth -> nlpModel.depth.toString,
            RandomForestParametersNames.impurity -> nlpModel.impurity.toString,
            RandomForestParametersNames.max_bins -> nlpModel.max_bins.toString,
            RandomForestParametersNames.nClassWords -> nlpModel.nClassWords.toString,
            RandomForestParametersNames.num_trees -> nlpModel.num_trees.toString,
            RandomForestParametersNames.seed -> nlpModel.seed.toString,
            RandomForestParametersNames.subset_strategy -> nlpModel.subset_strategy)

          ModelData(
            Some(nlpModel._id),
            nlpModel.name,
            nlpModel.tag_tweets,
            RR,
            parameters,
            nlpModel.setPartition)
        case v if v == NLP.toString =>
          val nlpModel = m.asInstanceOf[NLPStanfordClassifier]
          val parameters = Map(
            StandfordParametersNames.aggFunction -> nlpModel.agregation_function_name.toString,
            BoostParametersNames.numIter -> nlpModel.num_iterations.toString)

          ModelData(
            Some(nlpModel._id),
            nlpModel.name,
            nlpModel.tag_tweets,
            NLP,
            parameters,
            nlpModel.setPartition)

        case v if v == VADER.toString =>
          val nlpModel = m.asInstanceOf[VaderClassifier]
          val parameters = Map[String,String]().empty

          ModelData(
            Some(nlpModel._id),
            nlpModel.name,
            nlpModel.tag_tweets,
            VADER,
            parameters,
            nlpModel.setPartition)

        case v if v == EMOJI.toString =>
          val nlpModel = m.asInstanceOf[EmojiClassifier]
          val parameters = Map[String,String]().empty

          ModelData(
            Some(nlpModel._id),
            nlpModel.name,
            nlpModel.tag_tweets,
            EMOJI,
            parameters,
            nlpModel.setPartition)
      }

      def toModel(v: ModelData): ModelClassifier = {

        v.type_classifier.toString match {
          case l if l == API.toString =>

            ApiClassifier(
              v._id.get,
              v.name,
              TextProccesingApiClient("api_client"),
              v.tag_tweets,
              (v.partitionConf))
          case l if l == BAYES.toString =>
            BayesClassifier(
              v._id.get,
              v.name,
              v.tag_tweets,
              v.parameters(BayesParametersNames.nClassWords).toInt,
              v.parameters(BayesParametersNames.type_model),
              v.parameters(BayesParametersNames.lambda).toDouble,
              (v.partitionConf)
            )

          case l if l == RR.toString =>
            RandomForestClassifier(
              v._id.get,
              v.name,
              v.parameters(RandomForestParametersNames.num_trees).toInt,
              v.tag_tweets,
              (v.partitionConf),
              v.parameters(RandomForestParametersNames.depth).toInt,
              v.parameters(RandomForestParametersNames.max_bins).toInt,
              v.parameters(RandomForestParametersNames.subset_strategy),
              v.parameters(RandomForestParametersNames.impurity),
              v.parameters(RandomForestParametersNames.seed).toInt,
              v.parameters(RandomForestParametersNames.nClassWords).toInt)
          case l if l == BOOSTING.toString =>
            GradientBoostingClassifier(
              v._id.get,
              v.name,
              v.parameters(BoostParametersNames.numIter).toInt,
              v.tag_tweets,
              (v.partitionConf),
              v.parameters(BoostParametersNames.depth).toInt,
              v.parameters(BoostParametersNames.strategy_boosting),
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
              v.tag_tweets,
              aggFunction,
              aggFunction.toFunction,
              (v.partitionConf))

          case l if l == VADER.toString =>

            VaderClassifier(
              v._id.get,
              v.name,
              v.tag_tweets,
              (v.partitionConf))

          case l if l == EMOJI.toString =>

            EmojiClassifier(
              v._id.get,
              v.name,
              v.tag_tweets,
              (v.partitionConf))
        }
      }
    }
}