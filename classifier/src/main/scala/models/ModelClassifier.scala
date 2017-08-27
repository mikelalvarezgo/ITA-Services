package models

import java.util.Properties

import client.ApiClient
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
import scala.concurrent.Future
import scala.reflect.internal.util.Statistics.Quantity
import scala.util.Try
import scala.concurrent.ExecutionContext.Implicits.global

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
  val type_classifier: ModelType
  var dataSet: Option[RDD[TweetInfo]] = None
  val setPartition:  Option[PartitionConf]

  def setRDD(rdd:RDD[TweetInfo]):ModelClassifier = {
    this.dataSet = Some(rdd)
    this
  }
  def prepareModel()(implicit sc:SparkContext):SetPartition


}


case class GradientBoostingClassifier
(_id: Id,
  name: String,
  num_iterations: Int,
  setPartition: Option[PartitionConf],
  depth: Int,
  strategy_boosting: String,
  listPositiveEmojis: List[String],
  listNegativeEmojis: List[String],
  nClassWords:Int,
  override val type_classifier:ModelType = BOOSTING)
  extends ModelClassifier {

  def trainModel(partition:SetPartition)(implicit sc:SparkContext):GradientBoostedTreesModel = {
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

  def evaluateModel(
    model:GradientBoostedTreesModel,
    partition:SetPartition)
    (implicit sc:SparkContext):ModelResult = {
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
        if (r._1 == 1 && r._2 ==1) {
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


  def runModel(
    model:GradientBoostedTreesModel,
    idExecution:Id,
    topicId:Id,
    partition:SetPartition)
    (implicit sc:SparkContext):RDD[TweetResult] = {
    val classifySet = boostingRDD(tagRDD(partition.trainingSet))
    classifySet.map{data=>
      val result = model.predict(data._1.features)
      val tweetResul = TweetResult(Id.generate, idTweet = data._3, idExecution =idExecution, this.type_classifier.toString.toString.toString,result )
      tweetResul
    }
  }

  def prepareModel()(implicit sc:SparkContext): SetPartition = {
      //We will only use tweets with emojis and bad emojis for train and validation sets
      val emojiTweeta = filterTweetsWithEmojis
      val nTweets = dataSet.get.count()
      val nEmojisTweets = emojiTweeta.count()
      if( emojiTweeta.count()/dataSet.get.count() < setPartition.get.getNotClassifySet) {
          logger.warn("[GradientBoostingClassifier] Dataset does not contain enough tweets for Partition required")
          val relativeTrain = nTweets/nEmojisTweets * setPartition.get.train
          val relativeValidation = nTweets/nEmojisTweets * setPartition.get.validation
          val Array(train,validation) = emojiTweeta.randomSplit(Array(relativeTrain, (1- relativeValidation)))
          val classifySet = dataSet.get.subtract(emojiTweeta)
          SetPartition(train,validation,classifySet)
      }else {
          val relativeTrain = nTweets/nEmojisTweets * setPartition.get.train
          val relativeValidation = nTweets/nEmojisTweets * setPartition.get.validation

          val Array(train,validation,rest) = emojiTweeta.randomSplit(Array(relativeTrain, relativeValidation, 1 -(relativeTrain + relativeValidation)))
          val classifySet = dataSet.get.subtract(emojiTweeta).union(rest)
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

  def filterTweetsWithEmojis()(implicit sc:SparkContext):RDD[TweetInfo] = {
    dataSet.get.filter(tweet => tweet.contain_emoji)
  }
}

case class ApiClassifier(
  _id: Id,
  name: String,
  client:ApiClient,
  setPartition: Option[PartitionConf],
  listPositiveEmojis: List[String],
  listNegativeEmojis: List[String],
  override val type_classifier:ModelType = API)
  extends ModelClassifier{

  def tagRDD(tweetRDD:RDD[TweetInfo]):RDD[(Double,String,Id)] = {

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
          (isPositive, msgSanitized,tweet._id.get)
        }
      })
    records.filter(_.isFailure).map(_.get)
  }

  def runModel(idExecution:Id)(implicit sc:SparkContext):Future[List[TweetResult]] = {

    val fResult = dataSet.get.toLocalIterator.toList.map{tweet =>
        client.AnalizeText(tweet.tweetText).map(response => {

          TweetResult(Id.generate,tweet._id.get,idExecution,this.type_classifier.toString,response.returnScore)
        })
    }
    Future.sequence(fResult)
  }

  def evaluateModel(partition:SetPartition)(implicit sc:SparkContext): Future[ModelResult] = {
    val trainSet = (tagRDD(partition.trainingSet))
    val validationSet = (tagRDD(partition.validationSet))
    val sentimentText = List("Very Negative", "Negative", "Neutral", "Positive", "Very Positive")
    val props = new Properties()
    props.put("annotators", "tokenize, ssplit, parse, sentiment")
    val pipeline = new StanfordCoreNLP(props)
    val broadCastedPipeline = sc.broadcast(pipeline)
    val broadCastedSentimentText = sc.broadcast(sentimentText)

    var flabelAndPredsTrain = trainSet.toLocalIterator.toList.map { point =>
      client.AnalizeText(point._2).map{resp =>(Tuple2(point._1, resp.returnScore))}
      // Aggregate scores : Mean, Max, Min,

    }
    var flabelAndPredsValid = validationSet.toLocalIterator.toList.map { point =>
      client.AnalizeText(point._2).map{resp =>(Tuple2(point._1, resp.returnScore))}
      // Aggregate scores : Mean, Max, Min,
    }

    //Since Spark has done the heavy lifting already, lets pull the results back to the driver machine.
    //Calling collect() will bring the results to a single machine (the driver) and will convert it to a Scala array.

    //Start with the Training Set
    for {
      labelAndPredsTrain <- Future.sequence(flabelAndPredsTrain)
      labelAndPredsValid <- Future.sequence(flabelAndPredsTrain)
    }yield {

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

    def prepareModel()(implicit sc:SparkContext): SetPartition = {
    //We will only use tweets with emojis and bad emojis for train and validation sets
    val emojiTweeta = filterTweetsWithEmojis
    val nTweets = dataSet.get.count()
    val nEmojisTweets = emojiTweeta.count()
    if( emojiTweeta.count()/dataSet.get.count() < setPartition.get.getNotClassifySet) {
      logger.warn("[GradientBoostingClassifier] Dataset does not contain enough tweets for Partition required")
      val relativeTrain = nTweets/nEmojisTweets * setPartition.get.train
      val relativeValidation = nTweets/nEmojisTweets * setPartition.get.validation
      val Array(train,validation) = emojiTweeta.randomSplit(Array(relativeTrain, (1- relativeValidation)))
      val classifySet = dataSet.get.subtract(emojiTweeta)
      SetPartition(train,validation,classifySet)
    }else {
      val relativeTrain = nTweets/nEmojisTweets * setPartition.get.train
      val relativeValidation = nTweets/nEmojisTweets * setPartition.get.validation

      val Array(train,validation,rest) = emojiTweeta.randomSplit(Array(relativeTrain, relativeValidation, 1 -(relativeTrain + relativeValidation)))
      val classifySet = dataSet.get.subtract(emojiTweeta).union(rest)
      SetPartition(train,validation,classifySet)
    }
  }
  def filterTweetsWithEmojis:RDD[TweetInfo] = {
    dataSet.get.filter(tweet => tweet.contain_emoji)
  }
}

case class NLPStanfordClassifier(
  _id: Id,
  name: String,
  num_iterations: Int,
  agregation_function_name: AggFunction,
  agregation_function: (List[Int]=> Double),
  listPositiveEmojis: List[String],
  listNegativeEmojis: List[String],
  setPartition: Option[PartitionConf],
  override val type_classifier:ModelType = NLP)
  extends ModelClassifier {
  def filterTweetsWithEmojis:RDD[TweetInfo] = {
    dataSet.get.filter(tweet => tweet.contain_emoji)
  }
  def prepareModel()(implicit sc:SparkContext): SetPartition = {
    //We will only use tweets with emojis and bad emojis for train and validation sets
    val emojiTweeta = filterTweetsWithEmojis
    val nTweets = dataSet.get.count()
    val nEmojisTweets = emojiTweeta.count()
    if( emojiTweeta.count()/dataSet.get.count() < setPartition.get.getNotClassifySet) {
      logger.warn("[GradientBoostingClassifier] Dataset does not contain enough tweets for Partition required")
      val relativeTrain = nTweets/nEmojisTweets * setPartition.get.train
      val relativeValidation = nTweets/nEmojisTweets * setPartition.get.validation
      val Array(train,validation) = emojiTweeta.randomSplit(Array(relativeTrain, (1- relativeValidation)))
      val classifySet = dataSet.get.subtract(emojiTweeta)
      SetPartition(train,validation,classifySet)
    }else {
      val relativeTrain = nTweets/nEmojisTweets * setPartition.get.train
      val relativeValidation = nTweets/nEmojisTweets * setPartition.get.validation

      val Array(train,validation,rest) = emojiTweeta.randomSplit(Array(relativeTrain, relativeValidation, 1 -(relativeTrain + relativeValidation)))
      val classifySet = dataSet.get.subtract(emojiTweeta).union(rest)
      SetPartition(train,validation,classifySet)
    }
  }


  def evaluateModel(partition:SetPartition)(implicit sc:SparkContext):ModelResult = {
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
      val scores = sentenceList.map{sentence =>
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
        if (r._1 == 1 && r._2 ==1) {
          trainhappyCorrect += 1
        } else if (r._1 == 0 && r._2 == 0) {
          trainunhappyCorrect += 1
        }
      }
    )
    logger.info(s"[NLP-$name]unhappy messages in Training Set: " + trainunhappyTotal + " happy messages: " + trainhappyTotal)
    logger.info(s"[NLP-$name]happy % correct: " + trainhappyCorrect.toDouble/trainhappyTotal)
    logger.info(s"[NLP-$name]unhappy % correct: " + trainunhappyCorrect.toDouble/trainunhappyTotal)

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
        if (r._1 == 1 && r._2 ==1) {
          happyCorrect += 1
        } else if (r._1 == 0 && r._2 == 0) {
          unhappyCorrect += 1
        }
      }
    )
    logger.info(s"[NLP-$name] unhappy messages in Validation Set: " + unhappyTotal + " happy messages: " + happyTotal)
    logger.info(s"[NLP-$name]happy % correct: " + happyCorrect.toDouble/happyTotal)
    logger.info(s"[NLP-$name]unhappy % correct: " + unhappyCorrect.toDouble/unhappyTotal)

    val testErr = labelAndPredsValid.filter(r => r._1 != r._2).count.toDouble / validationSet.count()
    println(s"[NLP-$name]Test Error Validation Set: " + testErr)
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

  def tagRDD(tweetRDD:RDD[TweetInfo]):RDD[(Double,String,Id)] = {

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
            (isPositive, msgSanitized,tweet._id.get)
          }
        })
      records.filter(_.isFailure).map(_.get)
    }
  def runModel(idExecution:Id)(implicit sc:SparkContext): RDD[TweetResult] = {
      val sentimentText = List("Very Negative", "Negative", "Neutral", "Positive", "Very Positive")
      val props = new Properties()
      props.put("annotators", "tokenize, ssplit, parse, sentiment")
      val pipeline = new StanfordCoreNLP(props)
      val broadCastedPipeline = sc.broadcast(pipeline)
      val broadCastedSentimentText = sc.broadcast(sentimentText)
      val scoredSentiments = dataSet.get.map{
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
          TweetResult(Id.generate,tweet._id.get,idExecution,this.type_classifier.toString, aggScore.toDouble)
      }
    scoredSentiments
  }

}




