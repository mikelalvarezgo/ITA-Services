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

  def prepareModel()(implicit sc: SparkContext): SetPartition


}
case class RandomForestClassifier
(_id: Id,
  name: String,
  num_trees: Int,
  tag_tweets:String, //emoji or vader
  setPartition: Option[PartitionConf],
  depth: Int,
  max_bins: Int,
  subset_strategy:String,
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
    val model = RandomForestModel.load(sc,
      config.getString("models.path") +s"randomforest_${_id.value}")
    val trainSet = boostingRDD(tagRDD(partition.trainingSet))
    val validationSet = boostingRDD(tagRDD(partition.validationSet))

    var labelAndPredsTrain = trainSet.map { point =>
      val prediction = model.predict(point._1.features)
      Tuple2(point._1.label, prediction)
    }
    var labelAndPredsValid = validationSet.map { point =>
      val prediction = model.predict(point._1.features)
      Tuple2(point._1.label, prediction)
    }

    //Since Spark has done the heavy lifting already, lets pull the results back to the driver machine.
    //Calling collect() will bring the results to a single machine (the driver) and will convert it to a Scala array.

    //Start with the Training Set
    val result = labelAndPredsTrain.collect()
    var trainhappyTotal = 0
    var trainunhappyTotal = 0
    var trainhappyCorrect = 0
    var trainunhappyCorrect = 0
    result.foreach(
      r => {
        if (r._1 == 1) {
          trainhappyTotal += 1
        } else if (r._1 == 0) {
          trainunhappyTotal += 1
        }
        if (r._1 == 1 && r._2 == 1) {
          trainhappyCorrect += 1
        } else if (r._1 == 0 && r._2 == 0) {
          trainunhappyCorrect += 1
        }
      }
    )
    logger.info(s"[GradientBoostingClassifier-$name]unhappy messages in Training Set: " + trainunhappyTotal + " happy messages: " + trainhappyTotal)
    logger.info(s"[GradientBoostingClassifier-$name]happy % correct: " + trainhappyCorrect.toDouble / trainhappyTotal)
    logger.info(s"[GradientBoostingClassifier-$name]unhappy % correct: " + trainunhappyCorrect.toDouble / trainunhappyTotal)

    val traintestErr = labelAndPredsTrain.filter(r => r._1 != r._2).count.toDouble / trainSet.count()
    logger.info(s"[GradientBoostingClassifier-$name]Test Error Training Set: " + traintestErr)

    //Compute error for validation Set
    val resultsVal = labelAndPredsValid.collect()

    var happyTotal = 0
    var unhappyTotal = 0
    var happyCorrect = 0
    var unhappyCorrect = 0
    resultsVal.foreach(
      r => {
        if (r._1 == 1) {
          happyTotal += 1
        } else if (r._1 == 0) {
          unhappyTotal += 1
        }
        if (r._1 == 1 && r._2 == 1) {
          happyCorrect += 1
        } else if (r._1 == 0 && r._2 == 0) {
          unhappyCorrect += 1
        }
      }
    )
    logger.info(s"[GradientBoostingClassifier-$name] unhappy messages in Validation Set: " + unhappyTotal + " happy messages: " + happyTotal)
    logger.info(s"[GradientBoostingClassifier-$name]happy % correct: " + happyCorrect.toDouble / happyTotal)
    logger.info(s"[GradientBoostingClassifier-$name]unhappy % correct: " + unhappyCorrect.toDouble / unhappyTotal)

    val testErr = labelAndPredsValid.filter(r => r._1 != r._2).count.toDouble / validationSet.count()
    println(s"[GradientBoostingClassifier-$name]Test Error Validation Set: " + testErr)
    ModelResult(
      Id.generate,
      _id,
      trainhappyCorrect,
      trainhappyTotal - trainhappyCorrect,
      trainunhappyCorrect,
      trainunhappyTotal - trainunhappyCorrect,
      happyCorrect,
      happyTotal - happyCorrect,
      unhappyCorrect,
      unhappyTotal - unhappyCorrect)
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

      val Array(train, validation, rest) = emojiTweeta.randomSplit(Array(relativeTrain, relativeValidation, 1 - (relativeTrain + relativeValidation)))
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
              var score: Double = analyzer.polarityScores(msg).compound
              val isPositive = if (score < 0) 0 else if (score> 1.0) 1.0 else score
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
        val records = tweetRDD.map(
          tweet => {
            Try {
              val msg = EmojiParser.parseToAliases(tweet.tweetText.toString.toLowerCase())
              var score: Double = EmojiSentiText.totalPolarityScores(msg).compound
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
    val labeledSet = tagRDD(partition.trainingSet)
    val nlabeledSet = labeledSet.count()
    val trainSet = boostingRDD(labeledSet)
    val boostingStrategy = BoostingStrategy.defaultParams("Classification")
    boostingStrategy.setNumIterations(num_iterations) //number of passes over our training data
    boostingStrategy.treeStrategy.setNumClasses(2) //We have two output classes: happy and sad
    boostingStrategy.treeStrategy.setMaxDepth(depth)
    //Depth of each tree. Higher numbers mean more parameters, which can cause overfitting.
    //Lower numbers create a simpler model, which can be more accurate.
    //In practice you have to tweak this number to find the best value.
    val trainNumber = trainSet.count()
    val elem = trainSet.take(1).head
    val pathModels = config.getString("models.path") +s"gradient_${_id.value}"
    val startTimeMillis = System.currentTimeMillis()
    val model = GradientBoostedTrees.train(trainSet.map(_._1), boostingStrategy)
    val endTimeMillis = System.currentTimeMillis()
    val durationSeconds = (endTimeMillis - startTimeMillis) / 1000
    logger.info(s"[BOOSTIN-MODEL] Time of " +
      s"training of classifier ${_id.value} is $durationSeconds seconds")
    model.save(sc, pathModels)
    model
  }

  def evaluateModel(
    model: GradientBoostedTreesModel,
    partition: SetPartition)
    (implicit sc: SparkContext): ModelResult = {
    val trainSet = boostingRDD(tagRDD(partition.trainingSet))
    val validationSet = boostingRDD(tagRDD(partition.validationSet))

    var labelAndPredsTrain = trainSet.map { point =>
      val prediction = model.predict(point._1.features)
      Tuple2(point._1.label, prediction)
    }
    var labelAndPredsValid = validationSet.map { point =>
      val prediction = model.predict(point._1.features)
      Tuple2(point._1.label, prediction)
    }

    //Since Spark has done the heavy lifting already, lets pull the results back to the driver machine.
    //Calling collect() will bring the results to a single machine (the driver) and will convert it to a Scala array.

    //Start with the Training Set
    val result = labelAndPredsTrain.collect()
    var trainhappyTotal = 0
    var trainunhappyTotal = 0
    var trainhappyCorrect = 0
    var trainunhappyCorrect = 0
    result.foreach(
      r => {
        if (r._1 == 1) {
          trainhappyTotal += 1
        } else if (r._1 == 0) {
          trainunhappyTotal += 1
        }
        if (r._1 == 1 && r._2 == 1) {
          trainhappyCorrect += 1
        } else if (r._1 == 0 && r._2 == 0) {
          trainunhappyCorrect += 1
        }
      }
    )
    logger.info(s"[GradientBoostingClassifier-$name]unhappy messages in Training Set: " + trainunhappyTotal + " happy messages: " + trainhappyTotal)
    logger.info(s"[GradientBoostingClassifier-$name]happy % correct: " + trainhappyCorrect.toDouble / trainhappyTotal)
    logger.info(s"[GradientBoostingClassifier-$name]unhappy % correct: " + trainunhappyCorrect.toDouble / trainunhappyTotal)

    val traintestErr = labelAndPredsTrain.filter(r => r._1 != r._2).count.toDouble / trainSet.count()
    logger.info(s"[GradientBoostingClassifier-$name]Test Error Training Set: " + traintestErr)

    //Compute error for validation Set
    val resultsVal = labelAndPredsValid.collect()

    var happyTotal = 0
    var unhappyTotal = 0
    var happyCorrect = 0
    var unhappyCorrect = 0
    resultsVal.foreach(
      r => {
        if (r._1 == 1) {
          happyTotal += 1
        } else if (r._1 == 0) {
          unhappyTotal += 1
        }
        if (r._1 == 1 && r._2 == 1) {
          happyCorrect += 1
        } else if (r._1 == 0 && r._2 == 0) {
          unhappyCorrect += 1
        }
      }
    )
    logger.info(s"[GradientBoostingClassifier-$name] unhappy messages in Validation Set: " + unhappyTotal + " happy messages: " + happyTotal)
    logger.info(s"[GradientBoostingClassifier-$name]happy % correct: " + happyCorrect.toDouble / happyTotal)
    logger.info(s"[GradientBoostingClassifier-$name]unhappy % correct: " + unhappyCorrect.toDouble / unhappyTotal)

    val testErr = labelAndPredsValid.filter(r => r._1 != r._2).count.toDouble / validationSet.count()
    println(s"[GradientBoostingClassifier-$name]Test Error Validation Set: " + testErr)
    ModelResult(
      Id.generate,
      _id,
      trainhappyCorrect,
      trainhappyTotal - trainhappyCorrect,
      trainunhappyCorrect,
      trainunhappyTotal - trainunhappyCorrect,
      happyCorrect,
      happyTotal - happyCorrect,
      unhappyCorrect,
      unhappyTotal - unhappyCorrect)
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

      val Array(train, validation, rest) = emojiTweeta.randomSplit(Array(relativeTrain, relativeValidation, 1 - (relativeTrain + relativeValidation)))
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
              var score: Double = analyzer.polarityScores(msg).compound
              val isPositive = if (score < 0) 0 else if (score> 1.0) 1.0 else score
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
        val records = tweetRDD.map(
          tweet => {
            Try {
              val msg = EmojiParser.parseToAliases(tweet.tweetText.toString.toLowerCase())
              var score: Double = EmojiSentiText.totalPolarityScores(msg).compound
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
        val records = tweetRDD.map(
          tweet => {
            Try {
              val msg = EmojiParser.parseToAliases(tweet.tweetText.toString.toLowerCase())
              var score: Double = EmojiSentiText.totalPolarityScores(msg).compound
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

  def evaluateModel(partition: SetPartition)(implicit sc: SparkContext): Future[ModelResult] = {
    val trainSet = (tagRDD(partition.trainingSet))
    val validationSet = (tagRDD(partition.validationSet))
    val sentimentText = List("Very Negative", "Negative", "Neutral", "Positive", "Very Positive")
    val props = new Properties()
    props.put("annotators", "tokenize, ssplit, parse, sentiment")
    val pipeline = new StanfordCoreNLP(props)
    val broadCastedPipeline = sc.broadcast(pipeline)
    val broadCastedSentimentText = sc.broadcast(sentimentText)

    var flabelAndPredsTrain = trainSet.toLocalIterator.toList.map { point =>
      client.AnalizeText(point._2).map { resp => (Tuple2(point._1, resp.returnScore)) }
      // Aggregate scores : Mean, Max, Min,

    }
    var flabelAndPredsValid = validationSet.toLocalIterator.toList.map { point =>
      client.AnalizeText(point._2).map { resp => (Tuple2(point._1, resp.returnScore)) }
      // Aggregate scores : Mean, Max, Min,
    }

    //Since Spark has done the heavy lifting already, lets pull the results back to the driver machine.
    //Calling collect() will bring the results to a single machine (the driver) and will convert it to a Scala array.

    //Start with the Training Set
    for {
      labelAndPredsTrain <- Future.sequence(flabelAndPredsTrain)
      labelAndPredsValid <- Future.sequence(flabelAndPredsTrain)
    } yield {

      val result = labelAndPredsTrain
      var trainhappyTotal = 0
      var trainunhappyTotal = 0
      var trainhappyCorrect = 0
      var trainunhappyCorrect = 0
      result.foreach(
        r => {
          if (r._1 == 1) {
            trainhappyTotal += 1
          } else if (r._1 == 0) {
            trainunhappyTotal += 1
          }
          if (r._1 == 1 && r._2 == 1) {
            trainhappyCorrect += 1
          } else if (r._1 == 0 && r._2 == 0) {
            trainunhappyCorrect += 1
          }
        }
      )
      logger.info(s"[NLP-$name]unhappy messages in Training Set: " + trainunhappyTotal + " happy messages: " + trainhappyTotal)
      logger.info(s"[NLP-$name]happy % correct: " + trainhappyCorrect.toDouble / trainhappyTotal)
      logger.info(s"[NLP-$name]unhappy % correct: " + trainunhappyCorrect.toDouble / trainunhappyTotal)

      val traintestErr = labelAndPredsTrain.filter(r => r._1 != r._2).size.toDouble / trainSet.count()
      logger.info(s"[NLP-$name]Test Error Training Set: " + traintestErr)

      //Compute error for validation Set
      val resultsVal = labelAndPredsValid

      var happyTotal = 0
      var unhappyTotal = 0
      var happyCorrect = 0
      var unhappyCorrect = 0
      resultsVal.foreach(
        r => {
          if (r._1 == 1) {
            happyTotal += 1
          } else if (r._1 == 0) {
            unhappyTotal += 1
          }
          if (r._1 == 1 && r._2 == 1) {
            happyCorrect += 1
          } else if (r._1 == 0 && r._2 == 0) {
            unhappyCorrect += 1
          }
        }
      )
      logger.info(s"[NLP-$name] unhappy messages in Validation Set: " + unhappyTotal + " happy messages: " + happyTotal)
      logger.info(s"[NLP-$name]happy % correct: " + happyCorrect.toDouble / happyTotal)
      logger.info(s"[NLP-$name]unhappy % correct: " + unhappyCorrect.toDouble / unhappyTotal)

      val testErr = labelAndPredsValid.filter(r => r._1 != r._2).size.toDouble / validationSet.count()
      println(s"[NLP-$name]Test Error Validation Set: " + testErr)
      ModelResult(
        Id.generate,
        _id,
        trainhappyCorrect,
        trainhappyTotal - trainhappyCorrect,
        trainunhappyCorrect,
        trainunhappyTotal - trainunhappyCorrect,
        happyCorrect,
        happyTotal - happyCorrect,
        unhappyCorrect,
        unhappyTotal - unhappyCorrect)

    }
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

case class NLPStanfordClassifier(
  _id: Id,
  name: String,
  num_iterations: Int,
  tag_tweets:String, //emoji or vader
  agregation_function_name: AggFunction,
  agregation_function: (List[Int] => Double),
  setPartition: Option[PartitionConf],
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
        val records = tweetRDD.map(
          tweet => {
            Try {
              val msg = EmojiParser.parseToAliases(tweet.tweetText.toString.toLowerCase())
              var score: Double = EmojiSentiText.totalPolarityScores(msg).compound
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


  def evaluateModel(partition: SetPartition)(implicit sc: SparkContext): ModelResult = {
    val trainSet = (tagRDD(partition.trainingSet))
    val validationSet = (tagRDD(partition.validationSet))
    val sentimentText = List("Very Negative", "Negative", "Neutral", "Positive", "Very Positive")
    val props = new Properties()
    props.put("annotators", "tokenize, ssplit, parse, sentiment")
    val pipeline = new StanfordCoreNLP(props)
    val broadCastedPipeline = sc.broadcast(pipeline)
    val broadCastedSentimentText = sc.broadcast(sentimentText)

    var labelAndPredsTrain = trainSet.map { point =>
      val annotation = new Annotation(point._2)
      pipeline.annotate(annotation)
      val sentenceList = annotation.get(classOf[CoreAnnotations.SentencesAnnotation]).asScala.toList
      val scores = sentenceList.map { sentence =>
        val tree = sentence.get(classOf[SentimentCoreAnnotations.SentimentAnnotatedTree])
        RNNCoreAnnotations.getPredictedClass(tree)
      }
      // Aggregate scores : Mean, Max, Min,
      val aggScore = agregation_function(scores)
      Tuple2(point._1, aggScore.toDouble)
    }
    var labelAndPredsValid = validationSet.map { point =>
      val annotation = new Annotation(point._2)
      pipeline.annotate(annotation)
      val sentenceList = annotation.get(classOf[CoreAnnotations.SentencesAnnotation]).asScala.toList
      val scores = sentenceList.map { sentence =>
        val tree = sentence.get(classOf[SentimentCoreAnnotations.SentimentAnnotatedTree])
        RNNCoreAnnotations.getPredictedClass(tree)
      }
      // Aggregate scores : Mean, Max, Min,
      val aggScore = agregation_function(scores)
      Tuple2(point._1, aggScore.toDouble)
    }

    //Since Spark has done the heavy lifting already, lets pull the results back to the driver machine.
    //Calling collect() will bring the results to a single machine (the driver) and will convert it to a Scala array.

    //Start with the Training Set
    val result = labelAndPredsTrain.collect()
    var trainhappyTotal = 0
    var trainunhappyTotal = 0
    var trainhappyCorrect = 0
    var trainunhappyCorrect = 0
    result.foreach(
      r => {
        if (r._1 == 1) {
          trainhappyTotal += 1
        } else if (r._1 == 0) {
          trainunhappyTotal += 1
        }
        if (r._1 == 1 && r._2 == 1) {
          trainhappyCorrect += 1
        } else if (r._1 == 0 && r._2 == 0) {
          trainunhappyCorrect += 1
        }
      }
    )
    logger.info(s"[NLP-$name]unhappy messages in Training Set: " + trainunhappyTotal + " happy messages: " + trainhappyTotal)
    logger.info(s"[NLP-$name]happy % correct: " + trainhappyCorrect.toDouble / trainhappyTotal)
    logger.info(s"[NLP-$name]unhappy % correct: " + trainunhappyCorrect.toDouble / trainunhappyTotal)

    val traintestErr = labelAndPredsTrain.filter(r => r._1 != r._2).count.toDouble / trainSet.count()
    logger.info(s"[NLP-$name]Test Error Training Set: " + traintestErr)

    //Compute error for validation Set
    val resultsVal = labelAndPredsValid.collect()

    var happyTotal = 0
    var unhappyTotal = 0
    var happyCorrect = 0
    var unhappyCorrect = 0
    resultsVal.foreach(
      r => {
        if (r._1 == 1) {
          happyTotal += 1
        } else if (r._1 == 0) {
          unhappyTotal += 1
        }
        if (r._1 == 1 && r._2 == 1) {
          happyCorrect += 1
        } else if (r._1 == 0 && r._2 == 0) {
          unhappyCorrect += 1
        }
      }
    )
    logger.info(s"[NLP-$name] unhappy messages in Validation Set: " + unhappyTotal + " happy messages: " + happyTotal)
    logger.info(s"[NLP-$name]happy % correct: " + happyCorrect.toDouble / happyTotal)
    logger.info(s"[NLP-$name]unhappy % correct: " + unhappyCorrect.toDouble / unhappyTotal)

    val testErr = labelAndPredsValid.filter(r => r._1 != r._2).count.toDouble / validationSet.count()
    println(s"[NLP-$name]Test Error Validation Set: " + testErr)
    ModelResult(
      Id.generate,
      _id,
      trainhappyCorrect,
      trainhappyTotal - trainhappyCorrect,
      trainunhappyCorrect,
      trainunhappyTotal - trainunhappyCorrect,
      happyCorrect,
      happyTotal - happyCorrect,
      unhappyCorrect,
      unhappyTotal - unhappyCorrect)
  }


  def runModel(idExecution: Id)(implicit sc: SparkContext): RDD[TweetResult] = {
    val sentimentText = List("Very Negative", "Negative", "Neutral", "Positive", "Very Positive")
    val props = new Properties()
    props.put("annotators", "tokenize, ssplit, parse, sentiment")
    val pipeline = new StanfordCoreNLP(props)
    val broadCastedPipeline = sc.broadcast(pipeline)
    val broadCastedSentimentText = sc.broadcast(sentimentText)
    val scoredSentiments = dataSet.get.map {
      tweet =>
        val annotation = new Annotation(tweet.tweetText)
        pipeline.annotate(annotation)
        val sentenceList = annotation.get(classOf[CoreAnnotations.SentencesAnnotation]).asScala.toList
        val scores = sentenceList.map { sentence =>
          val tree = sentence.get(classOf[SentimentCoreAnnotations.SentimentAnnotatedTree])
          RNNCoreAnnotations.getPredictedClass(tree)
        }
        // Aggregate scores : Mean, Max, Min,
        val aggScore = agregation_function(scores)
        TweetResult(Id.generate, tweet._id.get, idExecution, this.type_classifier.toString, aggScore.toDouble)
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
    val hashingTF = new HashingTF(nClassWords)

    //Map the input strings to a tuple of labeled point + input text
    taggedRDD.map(
      t => (t._1, hashingTF.transform(t._2), t._2, t._3))
      .map(x => (new LabeledPoint((x._1).toDouble, x._2), x._3.fold("")((a, b) => a + " " + b), x._4))
  }

  def trainModel(partition: SetPartition)(implicit sc: SparkContext): NaiveBayesModel = {
    val trainSet = boostingRDD(tagRDD(partition.trainingSet))
    val pathModels = config.getString("models.path") +s"bayes_${_id.value}"

    //Depth of each tree. Higher numbers mean more parameters, which can cause overfitting.
    //Lower numbers create a simpler model, which can be more accurate.
    //In practice you have to tweak this number to find the best value.

    val startTimeMillis = System.currentTimeMillis()
    val naiveBayesModel: NaiveBayesModel = NaiveBayes.train(trainSet.map(_._1), lambda = 1.0, modelType = "multinomial")
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
      val Array(train, validation, rest) = emojiTweeta.randomSplit(Array(relativeTrain, relativeValidation, 1 - (relativeTrain + relativeValidation)))
      val classifySet = dataSet.get.subtract(emojiTweeta).union(rest)
      SetPartition(train, validation, classifySet)
    }
  }

  def evaluateModel(
    model: NaiveBayesModel,
    partition: SetPartition)
    (implicit sc: SparkContext): ModelResult = {
    val trainSet = boostingRDD(tagRDD(partition.trainingSet))
    val validationSet = boostingRDD(tagRDD(partition.validationSet))

    var labelAndPredsTrain = trainSet.map { point =>
      val prediction = model.predict(point._1.features)
      Tuple2(point._1.label, prediction)
    }
    var labelAndPredsValid = validationSet.map { point =>
      val prediction = model.predict(point._1.features)
      Tuple2(point._1.label, prediction)
    }

    //Since Spark has done the heavy lifting already, lets pull the results back to the driver machine.
    //Calling collect() will bring the results to a single machine (the driver) and will convert it to a Scala array.

    //Start with the Training Set
    val result = labelAndPredsTrain.collect()
    var trainhappyTotal = 0
    var trainunhappyTotal = 0
    var trainhappyCorrect = 0
    var trainunhappyCorrect = 0
    result.foreach(
      r => {
        if (r._1 == 1) {
          trainhappyTotal += 1
        } else if (r._1 == 0) {
          trainunhappyTotal += 1
        }
        if (r._1 == 1 && r._2 == 1) {
          trainhappyCorrect += 1
        } else if (r._1 == 0 && r._2 == 0) {
          trainunhappyCorrect += 1
        }
      }
    )
    logger.info(s"[NaiveBayesModel-$name]unhappy messages in Training Set: " + trainunhappyTotal + " happy messages: " + trainhappyTotal)
    logger.info(s"[NaiveBayesModel-$name]happy % correct: " + trainhappyCorrect.toDouble / trainhappyTotal)
    logger.info(s"[NaiveBayesModel-$name]unhappy % correct: " + trainunhappyCorrect.toDouble / trainunhappyTotal)

    val traintestErr = labelAndPredsTrain.filter(r => r._1 != r._2).count.toDouble / trainSet.count()
    logger.info(s"[NaiveBayesModel-$name]Test Error Training Set: " + traintestErr)

    //Compute error for validation Set
    val resultsVal = labelAndPredsValid.collect()

    var happyTotal = 0
    var unhappyTotal = 0
    var happyCorrect = 0
    var unhappyCorrect = 0
    resultsVal.foreach(
      r => {
        if (r._1 == 1) {
          happyTotal += 1
        } else if (r._1 == 0) {
          unhappyTotal += 1
        }
        if (r._1 == 1 && r._2 == 1) {
          happyCorrect += 1
        } else if (r._1 == 0 && r._2 == 0) {
          unhappyCorrect += 1
        }
      }
    )
    logger.info(s"[NaiveBayesModel-$name] unhappy messages in Validation Set: " + unhappyTotal + " happy messages: " + happyTotal)
    logger.info(s"[NaiveBayesModel-$name]happy % correct: " + happyCorrect.toDouble / happyTotal)
    logger.info(s"[NaiveBayesModel-$name]unhappy % correct: " + unhappyCorrect.toDouble / unhappyTotal)

    val testErr = labelAndPredsValid.filter(r => r._1 != r._2).count.toDouble / validationSet.count()
    println(s"[NaiveBayesModel-$name]Test Error Validation Set: " + testErr)
    ModelResult(
      Id.generate,
      _id,
      trainhappyCorrect,
      trainhappyTotal - trainhappyCorrect,
      trainunhappyCorrect,
      trainunhappyTotal - trainunhappyCorrect,
      happyCorrect,
      happyTotal - happyCorrect,
      unhappyCorrect,
      unhappyTotal - unhappyCorrect)
  }


  def tagRDD(tweetRDD: RDD[TweetInfo]): RDD[(Double, Seq[String], Id)] = tag_tweets match {

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
            (score, msgSanitized.split(" ").toSeq, tweet._id.get)
          }.recover{
            case e:Exception =>
              logger.error("Error parsinf tweet", e)
              throw e}
        })
      records.filter(_.isSuccess).map(_.get)
    case v if (v == "emoji") =>
      val records = tweetRDD.map(
        tweet => {
          Try {
            val msg = EmojiParser.parseToAliases(tweet.tweetText.toString.toLowerCase())
            var score: Double = EmojiSentiText.totalPolarityScores(msg).compound
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
    val dataBoost = boostingRDD(tagRDD(partition.classifySet))
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





