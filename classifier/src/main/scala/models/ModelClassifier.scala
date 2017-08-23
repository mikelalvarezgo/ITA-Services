package models

import java.util.Properties

import com.lightbend.emoji.Emoji
import domain.{Id, TweetInfo}
import org.apache.commons.collections.map.IdentityMap
import org.apache.spark.SparkContext
import org.apache.spark.mllib.feature.HashingTF
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.tree.GradientBoostedTrees
import org.apache.spark.mllib.tree.configuration.BoostingStrategy
import org.apache.spark.rdd.RDD
import results.{ModelResult, TweetResult}
import edu.stanford.nlp.ling.CoreAnnotations
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import com.vdurmont.emoji.EmojiParser
import edu.stanford.nlp.pipeline.{Annotation, StanfordCoreNLP}
import org.apache.spark.mllib.tree.model.GradientBoostedTreesModel
import utils.{Config, Logger}

import scala.collection.JavaConverters._
import scala.reflect.internal.util.Statistics.Quantity
import scala.util.Try

case class PartitionConf(
  train: Double,
  validation:Double,
  classification:Double) {
  def getNotClassifySet: Double = train + validation
  def getNumberOfTrain(quantity: Long):Long = (quantity *  train).toLong
  def getNumberOfValidation(quantity: Long):Long = (quantity *  validation).toLong
  def getNumberOfClassify(quantity: Long):Long = (quantity *  classification).toLong

}

  case class SetPartition(
    trainingSet: RDD[TweetInfo],
    validationSet: RDD[TweetInfo],
    classifySet: RDD[TweetInfo]) {
  }

sealed trait ModelClassifier extends  Logger with Config{

  val _id:Id
  val name:String
  val type_classifier: String
  val dataSet: RDD[TweetInfo]
  val setPartition:  Option[PartitionConf]
  val sc:SparkContext


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
  strategy_boosting: String,
  listPositiveEmojis: List[String],
  listNegativeEmojis: List[String],
  sc: SparkContext,
  nClassWords:Int,
  override val type_classifier:String = "gradient_boosting")
  extends ModelClassifier {

  def trainModel(partition:SetPartition):GradientBoostedTreesModel = {
    val trainSet = boostingRDD(tagRDD(partition.trainingSet))
    val boostingStrategy = BoostingStrategy.defaultParams("Classification")
    boostingStrategy.setNumIterations(num_iterations) //number of passes over our training data
    boostingStrategy.treeStrategy.setNumClasses(2) //We have two output classes: happy and sad
    boostingStrategy.treeStrategy.setMaxDepth(depth)
    //Depth of each tree. Higher numbers mean more parameters, which can cause overfitting.
    //Lower numbers create a simpler model, which can be more accurate.
    //In practice you have to tweak this number to find the best value.

    val model = GradientBoostedTrees.train(trainSet.map(_._1), boostingStrategy)
    model
  }

  def evaluateModel(model:GradientBoostedTreesModel, partition:SetPartition):ModelResult = {
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
        }if (r._1 == 1 && r._2 ==1) {
          trainhappyCorrect += 1
        } else if (r._1 == 0 && r._2 == 0) {
          trainunhappyCorrect += 1
        }
      }
    )
    logger.info(s"[GradientBoostingClassifier-$name]unhappy messages in Training Set: " + trainunhappyTotal + " happy messages: " + trainhappyTotal)
    logger.info(s"[GradientBoostingClassifier-$name]happy % correct: " + trainhappyCorrect.toDouble/trainhappyTotal)
    logger.info(s"[GradientBoostingClassifier-$name]unhappy % correct: " + trainunhappyCorrect.toDouble/trainunhappyTotal)

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
        if (r._1 == 1 && r._2 ==1) {
          happyCorrect += 1
        } else if (r._1 == 0 && r._2 == 0) {
          unhappyCorrect += 1
        }
      }
    )
    logger.info(s"[GradientBoostingClassifier-$name] unhappy messages in Validation Set: " + unhappyTotal + " happy messages: " + happyTotal)
    logger.info(s"[GradientBoostingClassifier-$name]happy % correct: " + happyCorrect.toDouble/happyTotal)
    logger.info(s"[GradientBoostingClassifier-$name]unhappy % correct: " + unhappyCorrect.toDouble/unhappyTotal)

    val testErr = labelAndPredsValid.filter(r => r._1 != r._2).count.toDouble / validationSet.count()
    println(s"[GradientBoostingClassifier-$name]Test Error Validation Set: " + testErr)
    ModelResult(
      Id.generate,
      _id,
      trainhappyCorrect,
      trainhappyTotal-trainhappyCorrect,
      trainunhappyCorrect,
      trainunhappyTotal- trainunhappyCorrect,
      happyCorrect,
      happyTotal -happyCorrect,
      unhappyCorrect,
      unhappyTotal -unhappyCorrect)
  }


  def runModel(model:GradientBoostedTreesModel, idExecution:Id, topicId:Id, partition:SetPartition):RDD[TweetResult] = {
    val classifySet = boostingRDD(tagRDD(partition.trainingSet))
    classifySet.map{data=>
      val result = model.predict(data._1.features)
      val tweetResul = TweetResult(Id.generate, idTweet = data._3, idExecution =idExecution, this.type_classifier,result )
      tweetResul
    }
  }

  def prepareModel: SetPartition = {
      //We will only use tweets with emojis and bad emojis for train and validation sets
      val emojiTweeta = filterTweetsWithEmojis
      val nTweets = dataSet.count()
      val nEmojisTweets = emojiTweeta.count()
      if( emojiTweeta.count()/dataSet.count() < setPartition.get.getNotClassifySet) {
          logger.warn("[GradientBoostingClassifier] Dataset does not contain enough tweets for Partition required")
          val relativeTrain = nTweets/nEmojisTweets * setPartition.get.train
          val relativeValidation = nTweets/nEmojisTweets * setPartition.get.validation
          val Array(train,validation) = emojiTweeta.randomSplit(Array(relativeTrain, (1- relativeValidation)))
          val classifySet = dataSet.subtract(emojiTweeta)
          SetPartition(train,validation,classifySet)
      }else {
          val relativeTrain = nTweets/nEmojisTweets * setPartition.get.train
          val relativeValidation = nTweets/nEmojisTweets * setPartition.get.validation

          val Array(train,validation,rest) = emojiTweeta.randomSplit(Array(relativeTrain, relativeValidation, 1 -(relativeTrain + relativeValidation)))
          val classifySet = dataSet.subtract(emojiTweeta).union(rest)
          SetPartition(train,validation,classifySet)
        }
  }

  def tagRDD(tweetRDD:RDD[TweetInfo]):RDD[(Double,Seq[String],Id)] = {

    val records = tweetRDD.map(
      tweet =>{
        Try{
          val msg = EmojiParser.parseToAliases(tweet.tweetText.toString.toLowerCase())
          var isPositive:Double =
            if (listPositiveEmojis.exists(msg.contains(_))) {
              1
            }else if (listNegativeEmojis.exists(msg.contains(_))){
              0
          }else 0.5
          var msgSanitized = EmojiParser.removeAllEmojis(msg)
          //Return a tuple
          (isPositive, msgSanitized.split(" ").toSeq,tweet._id.get)
        }
      })
    records.filter(_.isFailure).map(_.get)
  }

  def boostingRDD(
    taggedRDD:RDD[(Double,Seq[String], Id)]):RDD[(LabeledPoint, String, Id)] = {
    val hashingTF = new HashingTF(nClassWords)

    //Map the input strings to a tuple of labeled point + input text
    taggedRDD.map(
      t => (t._1, hashingTF.transform(t._2), t._2, t._3))
      .map(x => (new LabeledPoint((x._1).toDouble, x._2), x._3.fold("")((a,b) => a +" " +b),x._4))
  }

  def filterTweetsWithEmojis:RDD[TweetInfo] = {
    dataSet.filter(tweet => tweet.contain_emoji)
  }
}

case class ApiClassifier(
  _id: Id,
  name: String,
  num_iterations: Int,
  dataSet:RDD[TweetInfo],
  setPartition: Option[PartitionConf]){

  def runModel:ModelResult = ???

  def prepareModel: ModelResult = ???
}

case class NLPStanfordClassifier(
  _id: Id,
  name: String,
  num_iterations: Int,
  dataSet:RDD[TweetInfo],
  sc: SparkContext,
  agregation_function: (List[Int]=> Int),
  listPositiveEmojis: List[String],
  listNegativeEmojis: List[String],
  setPartition: Option[PartitionConf],
  override val type_classifier:String = "NLP_Standford") extends ModelClassifier {
  def filterTweetsWithEmojis:RDD[TweetInfo] = {
    dataSet.filter(tweet => tweet.contain_emoji)
  def prepareModel: SetPartition = {
    //We will only use tweets with emojis and bad emojis for train and validation sets
    val emojiTweeta = filterTweetsWithEmojis
    val nTweets = dataSet.count()
    val nEmojisTweets = emojiTweeta.count()
    if( emojiTweeta.count()/dataSet.count() < setPartition.get.getNotClassifySet) {
      logger.warn("[GradientBoostingClassifier] Dataset does not contain enough tweets for Partition required")
      val relativeTrain = nTweets/nEmojisTweets * setPartition.get.train
      val relativeValidation = nTweets/nEmojisTweets * setPartition.get.validation
      val Array(train,validation) = emojiTweeta.randomSplit(Array(relativeTrain, (1- relativeValidation)))
      val classifySet = dataSet.subtract(emojiTweeta)
      SetPartition(train,validation,classifySet)
    }else {
      val relativeTrain = nTweets/nEmojisTweets * setPartition.get.train
      val relativeValidation = nTweets/nEmojisTweets * setPartition.get.validation

      val Array(train,validation,rest) = emojiTweeta.randomSplit(Array(relativeTrain, relativeValidation, 1 -(relativeTrain + relativeValidation)))
      val classifySet = dataSet.subtract(emojiTweeta).union(rest)
      SetPartition(train,validation,classifySet)
    }
  }

    def tagRDD(tweetRDD:RDD[TweetInfo]):RDD[(Double,Seq[String],Id)] = {

      val records = tweetRDD.map(
        tweet =>{
          Try{
            val msg = EmojiParser.parseToAliases(tweet.tweetText.toString.toLowerCase())
            var isPositive:Double =
              if (listPositiveEmojis.exists(msg.contains(_))) {
                1
              }else if (listNegativeEmojis.exists(msg.contains(_))){
                0
              }else 0.5
            var msgSanitized = EmojiParser.removeAllEmojis(msg)
            //Return a tuple
            (isPositive, msgSanitized.split(" ").toSeq,tweet._id.get)
          }
        })
      records.filter(_.isFailure).map(_.get)
    }
  def runModel(idExecution:Id): RDD[TweetResult] = {
      val sentimentText = List("Very Negative", "Negative", "Neutral", "Positive", "Very Positive")
      val props = new Properties()
      props.put("annotators", "tokenize, ssplit, parse, sentiment")
      val pipeline = new StanfordCoreNLP(props)
      val broadCastedPipeline = sc.broadcast(pipeline)
      val broadCastedSentimentText = sc.broadcast(sentimentText)
      val scoredSentiments = dataSet.map{
        tweet =>
          val annotation = new Annotation(tweet.tweetText)
          pipeline.annotate(annotation)
          val sentenceList = annotation.get(classOf[CoreAnnotations.SentencesAnnotation]).asScala.toList
          val scores = sentenceList.map{sentence =>
            val tree = sentence.get(classOf[SentimentCoreAnnotations.SentimentAnnotatedTree])
            RNNCoreAnnotations.getPredictedClass(tree)
          }
          // Aggregate scores : Mean, Max, Min,
          val aggScore = agregation_function(scores)
          TweetResult(Id.generate,tweet._id.get,idExecution,this.type_classifier, aggScore.toDouble)
      }
    scoredSentiments
  }

  def prepareModel: SetPartition = ???
}




