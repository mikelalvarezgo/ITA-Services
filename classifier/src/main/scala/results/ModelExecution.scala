package results

import domain.{Id, Location, TweetInfo}
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}
import com.mongodb.spark._
import com.mongodb.spark.config.ReadConfig
import models._
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.functions.{max, min}
import org.apache.spark.{SparkConf, SparkContext}
import org.bson.Document
import org.dmg.pmml.ClusteringModel.ModelClass
import utils.{ClassifierDataContext, Config, Logger}
import domain.classifier.exception.ClassifierException
import domain.classifier.exception.ClassifierException._
import models.ModelConverter.modelConverter

import scala.concurrent.ExecutionContext.Implicits.global
import domain.Model._
import classifier.Model._
import org.apache.spark.mllib.tree.model.GradientBoostedTreesModel

import scala.concurrent.Future
import scala.util.Try



case class ModelExecution(
  _id: Option[Id],
  modelId: Id,
  topicId: Id,
  dateExecution: Long,
  status: String,
  resultModel: Option[ModelResult] =None
)//created, trained, runned
 extends Config with Logger{

  def chargeModel(dataSet: RDD[TweetInfo])(implicit dataContext:ClassifierDataContext):ModelClassifier = {
    val model =dataContext.modelDAO.get(modelId).getOrElse{
      logger.error(s"[EXECUTION] $modelId does not exist")
      throw ClassifierException(
        executionModelIdNotFound,
        s"Model $modelId does not exists in execution ${_id.get}")
    }
    modelConverter.toModel(model).setRDD(rdd = dataSet)
  }

  def getModelResult()(implicit dataContext:ClassifierDataContext, sc:SparkContext) : Future[ModelResult] = {
    val dataSet = extractData()
    val model = chargeModel(dataSet)
    evaluateModel(model)
  }
  def saveResult(tweets:List[TweetResult])(implicit dataContext:ClassifierDataContext, sc:SparkContext) :Future[Boolean] = {
    val saves = tweets.map{tweet =>
      dataContext.tweetResultDAO.create(tweet)
    }
    Future(saves.foldLeft(true)((res, t) => t.isSuccess && res) )

  }
  def executeModel()(implicit dataContext:ClassifierDataContext, sc:SparkContext) : Future[Boolean] = {
    val dataSet = extractData()
    val model = chargeModel(dataSet)
    (for {
      result <-runModel(dataSet, model)
      savedOk <- saveResult(result)
    }yield {
      savedOk
    }).recover{
      case e:Exception =>
        logger.error(s"Error when executing model ${_id.get} due to ${e.getMessage}",e)
        false
    }
  }

  def evaluateModel(model:ModelClassifier)(implicit dataContext:ClassifierDataContext, sc:SparkContext):Future[ModelResult] = {
    model.type_classifier match {
      case BAYES =>
        val bayesClassifier = model.asInstanceOf[BayesClassifier]
        val partition = bayesClassifier.prepareModel()(sc)
        val modelResult =
          bayesClassifier.evaluateModel(bayesClassifier.trainModel(partition)(sc),partition)(sc)
        Future(modelResult)
      case BOOSTING =>
        val boostModel = model.asInstanceOf[GradientBoostingClassifier]
        val partition = boostModel.prepareModel()(sc)
        val modelResult =
          boostModel.evaluateModel(boostModel.trainModel(partition)(sc),partition)(sc)
        Future(modelResult)
      case NLP =>
        val nlp = model.asInstanceOf[NLPStanfordClassifier]
        val partition = nlp.prepareModel()(sc)
        val modelResult =
          nlp.evaluateModel(partition)(sc)
        Future(modelResult)
      case API =>
        val nlp = model.asInstanceOf[ApiClassifier]
        val partition = nlp.prepareModel()(sc)
        val modelResult =
          nlp.evaluateModel(partition)(sc)
        modelResult
    }
  }

  def runModel(tweets:RDD[TweetInfo], model:ModelClassifier)(implicit sc: SparkContext):Future[List[TweetResult]] = {
   /* tweets.map{tweet   =>
      logger.info(s"[EXECUTION] $modelId STARTING EXECUTION ")



    } */
    model.type_classifier match {
      case BAYES =>
        val bayesClas = model.asInstanceOf[BayesClassifier]
        val partition = bayesClas.prepareModel()(sc)
        val modelTrained = bayesClas.trainModel(partition)(sc)
        val modelResult =
          bayesClas.runModel(this._id.get,modelTrained,partition)(sc)
        Future(modelResult.collect().toList)
      case BOOSTING =>
        val boostModel = model.asInstanceOf[GradientBoostingClassifier]
        val partition = boostModel.prepareModel()(sc)
        val pathModels = config.getString("models.path") +s"gradient_${boostModel._id.value}"
        val modelTrained = GradientBoostedTreesModel.load(sc, pathModels)
        val modelResult =
          boostModel.runModel(modelTrained,this._id.get,topicId,partition)(sc)
        Future(modelResult.collect().toList)
      case NLP =>
        val nlp = model.asInstanceOf[NLPStanfordClassifier]
        val partition = nlp.prepareModel()(sc)
        val modelResult =
          nlp.runModel(_id.get)(sc)
        Future(modelResult.collect().toList)
      case API =>
        val nlp = model.asInstanceOf[ApiClassifier]
        val partition = nlp.prepareModel()(sc)
        val modelResult =
          nlp.runModel(_id.get)(sc)
        modelResult
    }
  }


  def extractData()(implicit sc:SparkContext):RDD[TweetInfo] ={
    def parseDocument(doc:Document):TweetInfo ={
      val id = Some(Id(doc.getObjectId("_id").toHexString))
      val tweetId = doc.getLong("tweet_id")
      val createdAt = doc.getLong("createdAt")
      val latitude = None
      val t_user_id = Try{
        doc.getInteger("user_id")
      }.toOption
      val user_id = t_user_id.map(_.toLong).getOrElse(doc.getLong("user_id").toLong)
      val user_name = doc.getString("user_name")
      val user_followers = doc.getInteger("user_followers")
      val tweetText = doc.getString("tweetText")
      val lenguage = doc.getString("lenguage")
      val contain_emoji = doc.getBoolean("contain_emoji")
      val topic = Id(doc.getObjectId("topic").toHexString)
      TweetInfo(id,tweetId,createdAt,latitude,user_id,user_name,user_followers,tweetText,lenguage,contain_emoji,topic)
    }
    import com.mongodb.spark.config._
    val db = config.getString("mongodb.name")

    val readConfig = ReadConfig(Map(
      "uri" -> "mongodb://127.0.0.1",
      "database" -> db,
      "collection" -> "tweets")) // 1)
    val rdd =MongoSpark.load(sc, readConfig)
        .filter(doc => doc.getObjectId("topic").toHexString == topicId.value)
      .map(doc => parseDocument(doc))// 2)
    val a = rdd.take(1)
    rdd
  }

}
