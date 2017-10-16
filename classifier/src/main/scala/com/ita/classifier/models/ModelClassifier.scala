package com.ita.classifier.models

import java.util.Properties

import com.ita.classifier.client.ApiClient
import com.ita.classifier.lexicons.emojis.EmojiSentiText
import com.ita.classifier.lexicons.vader.SentimentIntensityAnalyzer
import com.ita.classifier.results.ModelResult
import com.ita.domain.utils.{Config, Logger}
import com.ita.domain.{Id, TweetInfo}
import com.ita.domain.TweetInfo
import org.apache.spark.SparkContext
import org.apache.spark.mllib.feature.HashingTF
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.tree.{GradientBoostedTrees, RandomForest}
import org.apache.spark.mllib.tree.configuration.{BoostingStrategy, Strategy}
import org.apache.spark.rdd.RDD
import com.ita.classifier.results.TweetResult
import edu.stanford.nlp.ling.CoreAnnotations
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import com.vdurmont.emoji.EmojiParser
import edu.stanford.nlp.pipeline.{Annotation, StanfordCoreNLP}
import org.apache.spark.mllib.evaluation.MulticlassMetrics
import org.apache.spark.ml.feature.StopWordsRemover
import org.apache.spark.mllib.classification.{NaiveBayes, NaiveBayesModel}
import org.apache.spark.mllib.tree.model.GradientBoostedTreesModel
import org.apache.spark.mllib.tree.RandomForest
import org.apache.spark.mllib.tree.model.RandomForestModel
import org.apache.spark.mllib.util.MLUtils
import org.joda.time.DateTime

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.reflect.internal.util.Statistics.Quantity
import scala.util.Try
import scala.concurrent.ExecutionContext.Implicits.global

case class PartitionConf(
  train: Double,
  validation: Double,
  classification: Double) {
  def getNotClassifySet: Double = train + validation

  def getNumberOfTrain(quantity: Long): Long = (quantity * train).toLong

  def getNumberOfValidation(quantity: Long): Long = (quantity * validation).toLong

  def getNumberOfClassify(quantity: Long): Long = (quantity * classification).toLong

}

case class SetPartition(
  trainingSet: RDD[TweetInfo],
  validationSet: RDD[TweetInfo],
  classifySet: RDD[TweetInfo]) {
}

sealed trait ModelClassifier extends Logger with Config {

  val _id: Id
  val name: String
  val type_classifier: ModelType
  var dataSet: Option[RDD[TweetInfo]] = None
  val setPartition: Option[PartitionConf]

  def setRDD(rdd: RDD[TweetInfo]): ModelClassifier = {
    this.dataSet = Some(rdd)
    this
  }



}
case class RandomForestClassifier
(_id: Id,
  name: String,
  num_trees: Int,
  tag_tweets:String, //emoji or vader
  setPartition: Option[PartitionConf],
  depth: Int,
  max_bins: Int,
  subset_strategy:String, //"auto", "all", ", "log2", "onethird".
 // value: String, //class or cont
  impurity: String,
  seed:Int,
  nClassWords: Int,
  override val type_classifier: ModelType = RR)
  extends ModelClassifier {

  def trainModel(partition: SetPartition)(implicit sc: SparkContext): RandomForestModel = {
    val labeledSet = tagRDD(partition.trainingSet)
    val nlabeledSet = labeledSet.count()
    val trainSet = boostingRDD(labeledSet)
    //Depth of each tree. Higher numbers mean more parameters, which can cause overfitting.
    //Lower numbers create a simpler model, which can be more accurate.
    //In practice you have to tweak this number to find the best value.
    val trainNumber = trainSet.count()
    val elem = trainSet.take(1).head
    val pathModels = config.getString("models.path") +s"randomforest_${_id.value}"
    val startTimeMillis = System.currentTimeMillis()
    val categories = Map[Int, Int]()
    val model = RandomForest.trainClassifier(trainSet.map(_._1), 3,categories,
      num_trees, subset_strategy, impurity, depth, max_bins)
    val endTimeMillis = System.currentTimeMillis()

    val durationSeconds = (endTimeMillis - startTimeMillis) / 1000
    logger.info(s"[RANDOMFOREST-MODEL] Time of " +
      s"training of classifier ${_id.value} is $durationSeconds seconds")
    model.save(sc, pathModels)
    model
  }

  def evaluateModel(
    partition: SetPartition)
    (implicit sc: SparkContext): ModelResult = {

    val startTimeMillis = System.currentTimeMillis()

    // Select example rows to display.
    // Select (prediction, true label) and compute test error

    val trainSet = boostingRDD(tagRDD(partition.trainingSet))
    val validationSet = boostingRDD(tagRDD(partition.validationSet))
    val testSet = boostingRDD(tagRDD(partition.classifySet))

    val model = RandomForestModel.load(sc,
      config.getString("models.path") +s"randomforest_${_id.value}")


    val trainpredictionAndLabel = trainSet.map(p => (model.predict(p._1.features), p._1.label))
    val train_accuracy = 1.0 * trainpredictionAndLabel.filter(x => x._1 == x._2).count() / trainSet.count()
    val valpredictionAndLabel = validationSet.map(p => (model.predict(p._1.features), p._1.label))
    val valid_accuracy = 1.0 * valpredictionAndLabel.filter(x => x._1 == x._2).count() / validationSet.count()
    val testpredictionAndLabel = testSet.map(p => (model.predict(p._1.features), p._1.label))
    val test_accuracy = 1.0 * testpredictionAndLabel.filter(x => x._1 == x._2).count() / testSet.count()
    val endTimeMillis = System.currentTimeMillis()
    val durationSeconds = (endTimeMillis - startTimeMillis) / 1000
    logger.info(s"[BOOSTING-MODEL] Time of " +
      s"training of classifier ${_id.value} is $durationSeconds seconds")
    ModelResult(Id.generate, _id, train_accuracy, valid_accuracy, test_accuracy)


  }

  def runModel(
    idExecution: Id,
    topicId: Id,
    partition: SetPartition)
    (implicit sc: SparkContext): RDD[TweetResult] = {
    val model = RandomForestModel.load(sc,
      config.getString("models.path") +s"randomforest_${_id.value}")
    val classifySet = boostingRDD(tagRDD(partition.trainingSet))
    classifySet.map { data =>
      val result = model.predict(data._1.features)
      val tweetResul = TweetResult(Id.generate, idTweet = data._3, idExecution = idExecution, this.type_classifier.toString.toString.toString, result)
      tweetResul
    }
  }

  def prepareModel()(implicit sc: SparkContext): SetPartition = {
    //We will only use tweets with emojis and bad emojis for train and validation sets
    val emojiTweeta = filterTweetsWithEmojis
    val nTweets = dataSet.get.count()
    val nEmojisTweets = emojiTweeta.count()
    tag_tweets match {

      case v if (v == "emoji") =>
        if (nEmojisTweets / nTweets < setPartition.get.getNotClassifySet) {
          logger.warn("[GradientBoostingClassifier] Dataset does not contain enough tweets for Partition required")
          val relativeTrain = nTweets / nEmojisTweets * setPartition.get.train
          val relativeValidation = nTweets / nEmojisTweets * setPartition.get.validation
          val trainNum = relativeTrain / (relativeTrain + relativeValidation)
          val Array(train, validation) = emojiTweeta.randomSplit(Array(trainNum, 1 - trainNum))
          val classifySet = dataSet.get.subtract(emojiTweeta)
          SetPartition(train, validation, classifySet)
        } else {
          val Array(train, validation, rest) =  dataSet.get.randomSplit(Array(setPartition.get.train,setPartition.get.validation ,setPartition.get.classification ))
          SetPartition(train, validation, rest)
        }
      case _=>
        val relativeTrain = nTweets / nEmojisTweets * setPartition.get.train
        val relativeValidation = nTweets / nEmojisTweets * setPartition.get.validation

        val Array(train, validation, rest) = dataSet.get.randomSplit(Array(setPartition.get.train,setPartition.get.validation ,setPartition.get.classification ))
        SetPartition(train, validation, rest)
    }

  }
  def scoreToClass(score:Double):Double = {
    if (score >= 1.333)
      2.0
    else if (score <= 0.666)
      0.0
    else
      1.0
  }
  def tagRDD(tweetRDD: RDD[TweetInfo]): RDD[(Double, Seq[String], Id)] = {
    tag_tweets match {

      case v if (v == "vader") =>
        val analyzer = new SentimentIntensityAnalyzer
        val records = tweetRDD.map(
          tweet => {
            Try {
              val msg = EmojiParser.parseToAliases(tweet.tweetText.toString.toLowerCase())
              var score: Double = analyzer.polarityScores(msg).to3Class
              var msgSanitized = EmojiParser.removeAllEmojis(msg)
              //Return a tuple
              (score, msgSanitized.split(" ").toSeq, tweet._id.get)
            }.recover{
              case e:Exception =>
                logger.error("Error parsinf tweet", e)
                throw e}
          })
        records.filter(_.isSuccess).map(_.get)
      case v if (v == "emoji") =>
        val analyzer = new EmojiSentiText

        val records = tweetRDD.map(
          tweet => {
            Try {
              val msg = EmojiParser.parseToAliases(tweet.tweetText.toString.toLowerCase())
              var score: Double = analyzer.totalPolarityScores(msg).compound
              var msgSanitized = EmojiParser.removeAllEmojis(msg)
              //Return a tuple
              (score, msgSanitized.split(" ").toSeq, tweet._id.get)
            }.recover{
              case e:Exception =>
                logger.error("Error parsinf tweet", e)
                throw e}
          })
        records.filter(_.isSuccess).map(_.get)
    }

  }

  def boostingRDD(
    taggedRDD: RDD[(Double, Seq[String], Id)]): RDD[(LabeledPoint, String, Id)] = {
    val hashingTF = new HashingTF(nClassWords)

    //Map the input strings to a tuple of labeled point + input text
    taggedRDD.map(
      t => (t._1, hashingTF.transform(t._2), t._2, t._3))
      .map(x => (new LabeledPoint((x._1).toDouble, x._2), x._3.fold("")((a, b) => a + " " + b), x._4))
  }

  def filterTweetsWithEmojis()(implicit sc: SparkContext): RDD[TweetInfo] = {
    dataSet.get.filter(tweet => tweet.contain_emoji)
  }
}


case class GradientBoostingClassifier
(_id: Id,
  name: String,
  num_iterations: Int,
  tag_tweets:String, //emoji or vader
  setPartition: Option[PartitionConf],
  depth: Int,
  strategy_boosting: String,
  nClassWords: Int,
  override val type_classifier: ModelType = BOOSTING)
  extends ModelClassifier {

  def trainModel(partition: SetPartition)(implicit sc: SparkContext): GradientBoostedTreesModel = {
    val startTimeMillis = System.currentTimeMillis()
    val labeledSet = tagRDD(partition.trainingSet)
    val nlabeledSet = labeledSet.count()
    val trainSet = boostingRDD(labeledSet)
    val boostingStrategy = BoostingStrategy.defaultParams("Regression")
    boostingStrategy.setNumIterations(num_iterations) //number of passes over our training data
    boostingStrategy.treeStrategy.setNumClasses(3)
    boostingStrategy.treeStrategy.setMaxDepth(depth)

    //Depth of each tree. Higher numbers mean more parameters, which can cause overfitting.
    //Lower numbers create a simpler model, which can be more accurate.
    //In practice you have to tweak this number to find the best value.
    val elem = trainSet.take(1).head
    val pathModels = config.getString("models.path") +s"gradient_${_id.value}"
    val model = GradientBoostedTrees.train(trainSet.map(_._1), boostingStrategy)
    val endTimeMillis = System.currentTimeMillis()
    val durationSeconds = (endTimeMillis - startTimeMillis) / 1000
    logger.info(s"[BOOSTING-MODEL] Time of " +
      s"training of classifier ${_id.value} is $durationSeconds seconds")
    model.save(sc, pathModels)
    model
  }
  def scoreToClass(score:Double):Double = {
    if (score >= 1.333)
      2.0
    else if (score <= 0.666)
      0.0
    else
      1.0
  }

  def evaluateModel(
    partition: SetPartition)
    (implicit sc: SparkContext): ModelResult = {
    val startTimeMillis = System.currentTimeMillis()


    // Select example rows to display.
    // Select (prediction, true label) and compute test error

    val trainSet = boostingRDD(tagRDD(partition.trainingSet))
    val validationSet = boostingRDD(tagRDD(partition.validationSet))
    val testSet = boostingRDD(tagRDD(partition.classifySet))

    val model = GradientBoostedTreesModel.load(sc,
      config.getString("models.path") +s"gradient_${_id.value}")


    val trainpredictionAndLabel = trainSet.map(p => (model.predict(p._1.features), p._1.label))
    val train_accuracy = 1.0 * trainpredictionAndLabel.filter(x => x._1 == x._2).count() / trainSet.count()
    val valpredictionAndLabel = validationSet.map(p => (model.predict(p._1.features), p._1.label))
    val valid_accuracy = 1.0 * valpredictionAndLabel.filter(x => x._1 == x._2).count() / validationSet.count()
    val testpredictionAndLabel = testSet.map(p => (model.predict(p._1.features), p._1.label))
    val test_accuracy = 1.0 * testpredictionAndLabel.filter(x => x._1 == x._2).count() / testSet.count()

    val endTimeMillis = System.currentTimeMillis()
    val durationSeconds = (endTimeMillis - startTimeMillis) / 1000
    logger.info(s"[GRADIENT-BOOSTING-MODEL] Time of " +
      s"evaluation of classifier ${_id.value} is $durationSeconds seconds")
    ModelResult(Id.generate, _id, train_accuracy, valid_accuracy, test_accuracy)


  }

  def runModel(
    idExecution: Id,
    topicId: Id,
    partition: SetPartition)
    (implicit sc: SparkContext): RDD[TweetResult] = {
    val model = GradientBoostedTreesModel.load(sc,
      config.getString("models.path") +s"gradient_${_id.value}")
    val classifySet = boostingRDD(tagRDD(partition.trainingSet))
    classifySet.map { data =>
      val result = model.predict(data._1.features)
      val tweetResul = TweetResult(Id.generate, idTweet = data._3, idExecution = idExecution, this.type_classifier.toString.toString.toString, result)
      tweetResul
    }
  }

  def prepareModel()(implicit sc: SparkContext): SetPartition = {
    //We will only use tweets with emojis and bad emojis for train and validation sets
    val emojiTweeta = filterTweetsWithEmojis
    val nTweets = dataSet.get.count()
    val nEmojisTweets = emojiTweeta.count()
    tag_tweets match {

      case v if (v == "emoji") =>
        if (nEmojisTweets / nTweets < setPartition.get.getNotClassifySet) {
          logger.warn("[GradientBoostingClassifier] Dataset does not contain enough tweets for Partition required")
          val relativeTrain = nTweets / nEmojisTweets * setPartition.get.train
          val relativeValidation = nTweets / nEmojisTweets * setPartition.get.validation
          val trainNum = relativeTrain / (relativeTrain + relativeValidation)
          val Array(train, validation) = emojiTweeta.randomSplit(Array(trainNum, 1 - trainNum))
          val classifySet = dataSet.get.subtract(emojiTweeta)
          SetPartition(train, validation, classifySet)
        } else {
          val relativeTrain = nTweets / nEmojisTweets * setPartition.get.train
          val relativeValidation = nTweets / nEmojisTweets * setPartition.get.validation

          val Array(train, validation, rest) =  dataSet.get.randomSplit(Array(setPartition.get.train,setPartition.get.validation ,setPartition.get.classification ))
          val classifySet = dataSet.get.subtract(emojiTweeta).union(rest)
          SetPartition(train, validation, classifySet)
        }
      case _=>
        val Array(train, validation, rest) = dataSet.get.randomSplit(Array(setPartition.get.train,setPartition.get.validation ,setPartition.get.classification ))
        val classifySet = dataSet.get.subtract(emojiTweeta).union(rest)
        SetPartition(train, validation, classifySet)
    }

  }

  def tagRDD(tweetRDD: RDD[TweetInfo]): RDD[(Double, Seq[String], Id)] = {
    tag_tweets match {

      case v if (v == "vader") =>
        val analyzer = new SentimentIntensityAnalyzer
        val records = tweetRDD.map(
          tweet => {
            Try {
              val msg = EmojiParser.parseToAliases(tweet.tweetText.toString.toLowerCase())
              var score: Double = analyzer.polarityScores(msg).to3Class
              val isPositive = (score +1)/2
              var msgSanitized = EmojiParser.removeAllEmojis(msg)

              //Return a tuple
              (isPositive, msgSanitized.split(" ").toSeq, tweet._id.get)
            }.recover{
              case e:Exception =>
                logger.error("Error parsinf tweet", e)
                throw e}
          })
        records.filter(_.isSuccess).map(_.get)
      case v if (v == "emoji") =>
        val analyzer = new EmojiSentiText
        val records = tweetRDD.map(
          tweet => {
            Try {
              val msg = EmojiParser.parseToAliases(tweet.tweetText.toString.toLowerCase())
              var score: Double = analyzer.totalPolarityScores(msg).compound
              var msgSanitized = EmojiParser.removeAllEmojis(msg)
              //Return a tuple
              (score, msgSanitized.split(" ").toSeq, tweet._id.get)
            }.recover{
              case e:Exception =>
                logger.error("Error parsinf tweet", e)
                throw e}
          })
        records.filter(_.isSuccess).map(_.get)
    }

  }

  def boostingRDD(
    taggedRDD: RDD[(Double, Seq[String], Id)]): RDD[(LabeledPoint, String, Id)] = {
    val hashingTF = new HashingTF(nClassWords)

    //Map the input strings to a tuple of labeled point + input text
    taggedRDD.map(
      t => (t._1, hashingTF.transform(t._2), t._2, t._3))
      .map(x => (new LabeledPoint((x._1).toDouble, x._2), x._3.fold("")((a, b) => a + " " + b), x._4))
  }

  def filterTweetsWithEmojis()(implicit sc: SparkContext): RDD[TweetInfo] = {
    dataSet.get.filter(tweet => tweet.contain_emoji)
  }
}

case class ApiClassifier(
  _id: Id,
  name: String,
  client: ApiClient,
  tag_tweets:String,
  setPartition: Option[PartitionConf],
  override val type_classifier: ModelType = API)
  extends ModelClassifier {

  def tagRDD(tweetRDD: RDD[TweetInfo]): RDD[(Double, String, Id)] =
    tag_tweets match {

      case v if (v == "vader") =>
        val analyzer = new SentimentIntensityAnalyzer
        val records = tweetRDD.map(
          tweet => {
            Try {
              val msg = EmojiParser.parseToAliases(tweet.tweetText.toString.toLowerCase())
              var score: Double = analyzer.polarityScores(msg).compound
              val isPositive = if (score < 0) 0 else if (score> 1.0) 1.0 else score
              var msgSanitized = EmojiParser.removeAllEmojis(msg)
              //Return a tuple
              (score, msgSanitized, tweet._id.get)
            }.recover{
              case e:Exception =>
                logger.error("Error parsinf tweet", e)
                throw e}
          })
        records.filter(_.isSuccess).map(_.get)
      case v if (v == "emoji") =>
        val analyzer = new EmojiSentiText

        val records = tweetRDD.map(
          tweet => {
            Try {
              val msg = EmojiParser.parseToAliases(tweet.tweetText.toString.toLowerCase())
              var score: Double = analyzer.totalPolarityScores(msg).compound
              var msgSanitized = EmojiParser.removeAllEmojis(msg)
              //Return a tuple
              (score, msgSanitized, tweet._id.get)
            }.recover{
              case e:Exception =>
                logger.error("Error parsinf tweet", e)
                throw e}
          })
        records.filter(_.isSuccess).map(_.get)
    }

  def runModel(idExecution: Id)(implicit sc: SparkContext): Future[List[TweetResult]] = {

    val fResult = dataSet.get.toLocalIterator.toList.map { tweet =>
      client.AnalizeText(tweet.tweetText).map(response => {

        TweetResult(Id.generate, tweet._id.get, idExecution, this.type_classifier.toString, response.returnScore)
      })
    }
    Future.sequence(fResult)
  }


  def prepareModel()(implicit sc: SparkContext): SetPartition = {
    //We will only use tweets with emojis and bad emojis for train and validation sets
    val emojiTweeta = filterTweetsWithEmojis
    val nTweets = dataSet.get.count()
    val nEmojisTweets = emojiTweeta.count()
    if (emojiTweeta.count() / dataSet.get.count() < setPartition.get.getNotClassifySet) {
      logger.warn("[GradientBoostingClassifier] Dataset does not contain enough tweets for Partition required")
      val relativeTrain = nTweets / nEmojisTweets * setPartition.get.train
      val relativeValidation = nTweets / nEmojisTweets * setPartition.get.validation
      val Array(train, validation) = emojiTweeta.randomSplit(Array(relativeTrain, (1 - relativeValidation)))
      val classifySet = dataSet.get.subtract(emojiTweeta)
      SetPartition(train, validation, classifySet)
    } else {
      val relativeTrain = nTweets / nEmojisTweets * setPartition.get.train
      val relativeValidation = nTweets / nEmojisTweets * setPartition.get.validation

      val Array(train, validation, rest) = emojiTweeta.randomSplit(Array(relativeTrain, relativeValidation, 1 - (relativeTrain + relativeValidation)))
      val classifySet = dataSet.get.subtract(emojiTweeta).union(rest)
      SetPartition(train, validation, classifySet)
    }
  }

  def filterTweetsWithEmojis: RDD[TweetInfo] = {
    dataSet.get.filter(tweet => tweet.contain_emoji)
  }
}

// NO SUPERVISED
case class NLPStanfordClassifier(
  _id: Id,
  name: String,
  num_iterations: Int,
  tag_tweets:String, //emoji or vader
  agregation_function_name: AggFunction, // MeanAgg
  agregation_function: (List[Int] => Double),
  setPartition: Option[PartitionConf] = None,
  override val type_classifier: ModelType = NLP)
  extends ModelClassifier {
  def filterTweetsWithEmojis: RDD[TweetInfo] = {
    dataSet.get.filter(tweet => tweet.contain_emoji)
  }

  def tagRDD(tweetRDD: RDD[TweetInfo]): RDD[(Double, String, Id)] =
    tag_tweets match {

      case v if (v == "vader") =>
        val analyzer = new SentimentIntensityAnalyzer
        val records = tweetRDD.map(
          tweet => {
            Try {
              val msg = EmojiParser.parseToAliases(tweet.tweetText.toString.toLowerCase())
              var score: Double = analyzer.polarityScores(msg).compound
              val isPositive = if (score < 0) 0 else if (score> 1.0) 1.0 else score
              var msgSanitized = EmojiParser.removeAllEmojis(msg)
              //Return a tuple
              (score, msgSanitized, tweet._id.get)
            }.recover{
              case e:Exception =>
                logger.error("Error parsinf tweet", e)
                throw e}
          })
        records.filter(_.isSuccess).map(_.get)
      case v if (v == "emoji") =>
        val analyzer = new EmojiSentiText
        val records = tweetRDD.map(
          tweet => {
            Try {
              val msg = EmojiParser.parseToAliases(tweet.tweetText.toString.toLowerCase())
              var score: Double = analyzer.totalPolarityScores(msg).compound
              var msgSanitized = EmojiParser.removeAllEmojis(msg)
              //Return a tuple
              (score, msgSanitized, tweet._id.get)
            }.recover{
              case e:Exception =>
                logger.error("Error parsinf tweet", e)
                throw e}
          })
        records.filter(_.isSuccess).map(_.get)
    }

  def runModel(idExecution: Id)(implicit sc: SparkContext): RDD[TweetResult] = {
    val scoredSentiments = dataSet.get.map {
      tweet =>
       val  score = NLPAnalizer.extractSentiment(tweet.tweetText)
        TweetResult(Id.generate, tweet._id.get, idExecution, this.type_classifier.toString, Sentiment.to3class(score))
    }
    scoredSentiments
  }
}


case class VaderClassifier(
  _id: Id,
  name: String,
  tag_tweets:String, //emoji or vader
  setPartition: Option[PartitionConf] = None,
  override val type_classifier: ModelType = VADER)
  extends ModelClassifier {
  def filterTweetsWithEmojis: RDD[TweetInfo] = {
    dataSet.get.filter(tweet => tweet.contain_emoji)
  }


  def runModel(idExecution: Id)(implicit sc: SparkContext): RDD[TweetResult] = {
    val analyzer = new SentimentIntensityAnalyzer
    val scoredSentiments = dataSet.get.map {
      tweet =>
        val sanitizedText = EmojiParser.removeAllEmojis(tweet.tweetText)
        var score: Double = analyzer.polarityScores(sanitizedText).compound

        // Aggregate scores : Mean, Max, Min,
        TweetResult(Id.generate, tweet._id.get, idExecution, this.type_classifier.toString, score.toDouble)
    }
    scoredSentiments
  }

}


case class EmojiClassifier(
  _id: Id,
  name: String,
  tag_tweets:String, //emoji or vader
  setPartition: Option[PartitionConf] = None,
  override val type_classifier: ModelType = EMOJI)
  extends ModelClassifier {
  def filterTweetsWithEmojis: RDD[TweetInfo] = {
    dataSet.get.filter(tweet => tweet.contain_emoji)
  }


  def runModel(idExecution: Id)(implicit sc: SparkContext): RDD[TweetResult] = {
    val analyzer = new EmojiSentiText
    val scoredSentiments = dataSet.get.map {
      tweet =>
        var score: Double = analyzer.totalPolarityScores(tweet.tweetText).compound
        TweetResult(Id.generate, tweet._id.get, idExecution, this.type_classifier.toString, score.toDouble)
    }
    scoredSentiments
  }

}


case class BayesClassifier(
  _id: Id,
  name: String,
  tag_tweets:String, //emoji or vader
  nClassWords: Int,
  type_model: String, //bernoulli or multinomial
  lambda: Double, //
  setPartition: Option[PartitionConf],
  override val type_classifier: ModelType = BAYES)
  extends ModelClassifier {
  def filterTweetsWithEmojis: RDD[TweetInfo] = {
    dataSet.get.filter(tweet => tweet.contain_emoji)
  }

  def boostingRDD(
    taggedRDD: RDD[(Double, Seq[String], Id)]): RDD[(LabeledPoint, String, Id)] = {
    val hashingTF = new HashingTF()
    //Map the input strings to a tuple of labeled point + input text
    taggedRDD.map(
      t => (t._1, hashingTF.transform(t._2), t._2, t._3))
      .map(x => (new LabeledPoint((x._1), x._2), x._3.fold("")((a, b) => a + " " + b), x._4))
  }

  def trainModel(partition: SetPartition)(implicit sc: SparkContext): NaiveBayesModel = {
    val trainSet = boostingRDD(tagRDD(partition.trainingSet))
    val pathModels = config.getString("models.path") +s"bayes_${_id.value}"
    val startTimeMillis = System.currentTimeMillis()
    val trainSetExample = trainSet.take(10)
    val trainTSet =  if (type_model =="bernoulli")
      trainSet.map(elem => LabeledPoint(elem._1.label.toInt, elem._1.features))
    else
      trainSet.map(_._1)
    val naiveBayesModel: NaiveBayesModel = NaiveBayes.train(trainTSet, lambda = lambda, modelType =type_model)
    naiveBayesModel.save(sc,pathModels)
    val endTimeMillis = System.currentTimeMillis()
    val durationSeconds = (endTimeMillis - startTimeMillis) / 1000
    logger.info(s"[BAYES-MODEL] Time of " +
      s"training of classifier ${_id.value} is $durationSeconds seconds")
    naiveBayesModel
  }

  def prepareModel()(implicit sc: SparkContext): SetPartition = {
    //We will only use tweets with emojis and bad emojis for train and validation sets
    val emojiTweeta = filterTweetsWithEmojis
    val nTweets = dataSet.get.count()
    val nEmojisTweets = emojiTweeta.count()
    tag_tweets match {

      case v if (v == "emoji") =>
        if (nEmojisTweets / nTweets < setPartition.get.getNotClassifySet) {
          logger.warn("[GradientBoostingClassifier] Dataset does not contain enough tweets for Partition required")
          val relativeTrain = nTweets / nEmojisTweets * setPartition.get.train
          val relativeValidation = nTweets / nEmojisTweets * setPartition.get.validation
          val trainNum = relativeTrain / (relativeTrain + relativeValidation)
          val Array(train, validation) = emojiTweeta.randomSplit(Array(trainNum, 1 - trainNum))
          val classifySet = dataSet.get.subtract(emojiTweeta)
          SetPartition(train, validation, classifySet)
        } else {
          val relativeTrain = nTweets / nEmojisTweets * setPartition.get.train
          val relativeValidation = nTweets / nEmojisTweets * setPartition.get.validation

          val Array(train, validation, rest) = emojiTweeta.randomSplit(Array(setPartition.get.train,setPartition.get.validation ,setPartition.get.classification ))
          val classifySet = dataSet.get.subtract(emojiTweeta).union(rest)
          SetPartition(train, validation, classifySet)
        }
      case _=>
        val relativeTrain = nTweets / nEmojisTweets * setPartition.get.train
        val relativeValidation = nTweets / nEmojisTweets * setPartition.get.validation

        val Array(train, validation, rest) =  dataSet.get.randomSplit(Array(setPartition.get.train,setPartition.get.validation ,setPartition.get.classification ))
        val classifySet = dataSet.get.subtract(emojiTweeta).union(rest)
        SetPartition(train, validation, classifySet)
    }

  }

  def evaluateModel(
    partition: SetPartition)
    (implicit sc: SparkContext): ModelResult = {

    val startTimeMillis = System.currentTimeMillis()

    // Select example rows to display.
    // Select (prediction, true label) and compute test error

    val trainSet = boostingRDD(tagRDD(partition.trainingSet))
    val validationSet = boostingRDD(tagRDD(partition.validationSet))
    val testSet = boostingRDD(tagRDD(partition.classifySet))

    val model = NaiveBayesModel.load(sc,
      config.getString("models.path") +s"bayes_${_id.value}")


    val trainpredictionAndLabel = trainSet.map(p => (model.predict(p._1.features), p._1.label))
    val train_accuracy = 1.0 * trainpredictionAndLabel.filter(x => x._1 == x._2).count() / trainSet.count()
    val valpredictionAndLabel = validationSet.map(p => (model.predict(p._1.features), p._1.label))
    val valid_accuracy = 1.0 * valpredictionAndLabel.filter(x => x._1 == x._2).count() / validationSet.count()
    val testpredictionAndLabel = testSet.map(p => (model.predict(p._1.features), p._1.label))
    val test_accuracy = 1.0 * testpredictionAndLabel.filter(x => x._1 == x._2).count() / testSet.count()

    val endTimeMillis = System.currentTimeMillis()
    val durationSeconds = (endTimeMillis - startTimeMillis) / 1000
    logger.info(s"[BAYES-MODEL] Time of " +
      s"evaluation of classifier ${_id.value} is $durationSeconds seconds")
    ModelResult(Id.generate, _id, train_accuracy, valid_accuracy, test_accuracy)


  }


  def tagRDD(tweetRDD: RDD[TweetInfo]): RDD[(Double, Seq[String], Id)] = tag_tweets match {
    case v if (v == "vader") =>
      val analyzer = new SentimentIntensityAnalyzer
      val depurer = new  StopWordsRemover("depurer")
      val records = tweetRDD.map(
        tweet => {
          Try {
            val msg = EmojiParser.parseToAliases(tweet.tweetText.toString.toLowerCase())
            var score: Double =  if (type_model == "bernoulli"){
              analyzer.polarityScores(msg).to2Class
            }else analyzer.polarityScores(msg).to3Class
            var msgSanitized = EmojiParser.removeAllEmojis(msg)
              .filter(s => !depurer.getStopWords.contains(s))
            (score, msgSanitized.split(" ").toSeq, tweet._id.get)
          }.recover{
            case e:Exception =>
              logger.error("Error parsinf tweet", e)
              throw e}
        })
      records.filter(_.isSuccess).map(_.get)
    case v if (v == "nlp") =>
      val analyzer = new EmojiSentiText
      val records = tweetRDD.map(
        tweet => {
          Try {
            val msg = EmojiParser.parseToAliases(tweet.tweetText.toString.toLowerCase())
            val  score = NLPAnalizer.extractSentiment(tweet.tweetText)
            var msgSanitized = EmojiParser.removeAllEmojis(msg)
            (Sentiment.to3class(score).toDouble/3,  msgSanitized.split(" ").toSeq, tweet._id.get)
          }.recover{
            case e:Exception =>
              logger.error("Error parsinf tweet", e)
              throw e}
        })
      records.filter(_.isSuccess).map(_.get)
    case v if (v == "emoji") =>
      val analyzer = new EmojiSentiText
      val records = tweetRDD.map(
        tweet => {
          Try {
            val msg = EmojiParser.parseToAliases(tweet.tweetText.toString.toLowerCase())
            var score: Double = analyzer.totalPolarityScores(msg).compound
            var msgSanitized = EmojiParser.removeAllEmojis(msg)
            //Return a tuple
            (score,  msgSanitized.split(" ").toSeq, tweet._id.get)
          }.recover{
            case e:Exception =>
              logger.error("Error parsinf tweet", e)
              throw e}
        })
      records.filter(_.isSuccess).map(_.get)
  }

  def runModel(idExecution: Id, partition: SetPartition)(implicit sc: SparkContext): RDD[TweetResult] = {
    val model = NaiveBayesModel.load(sc,config.getString("models.path") +s"bayes_${_id.value}"
    )
    var (posTweets,neutTweets, negTweets) = (0L,0L,0L)
    var dataBoost = boostingRDD(tagRDD(partition.classifySet))
    val scoredSentiments = dataBoost.map {
      tweet =>
        val score = model.predict(tweet._1.features)
        TweetResult(
          Id.generate,
          tweet._3,
          idExecution,
          this.type_classifier.toString,
          score.toDouble)
    }

    scoredSentiments
  }


}





