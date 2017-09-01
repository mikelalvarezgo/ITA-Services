package results

import domain.{Id, TweetInfo}
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

import scala.concurrent.Future
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
        val modelTrained = boostModel.trainModel(partition)(sc)
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

    val readConfig = ReadConfig(Map("uri" -> "mongodb://127.0.0.1/", "database" -> "test", "collection" -> "zips")) // 1)
    sc.loadFromMongoDB(readConfig).filter(tw =>
      tw.get[Id]("topic", classOf[Id]) == topicId).map(doc => doc.asInstanceOf[TweetInfo]) // 2)
  }

}
