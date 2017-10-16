package com.ita.classifier.results

import com.ita.classifier.utils.ClassifierDataContext
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}
import com.mongodb.spark._
import com.mongodb.spark.config.ReadConfig
import com.ita.classifier.models._
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.functions.{max, min}
import org.apache.spark.{SparkConf, SparkContext}
import org.bson.Document
import org.dmg.pmml.ClusteringModel.ModelClass
import com.ita.domain.classifier.exception.ClassifierException
import com.ita.domain.classifier.exception.ClassifierException._
import com.ita.classifier.models.ModelConverter.modelConverter

import scala.concurrent.ExecutionContext.Implicits.global
import com.ita.domain.Model._
import com.ita.domain.utils.{Config, Logger}
import com.ita.domain.{Id, TweetInfo}
import org.apache.spark.mllib.classification.NaiveBayesModel
import org.apache.spark.mllib.tree.model.{GradientBoostedTreesModel, RandomForestModel}
import com.ita.classifier.ClassifierService.dataContext

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
  def evaluateModel()(implicit dataContext:ClassifierDataContext, sc:SparkContext) : Future[ModelResult] = {
    val dataSet = extractData()
    val model = chargeModel(dataSet)
    if( status != "trained"){
      logger.warn(s"This  of Model ${model.type_classifier} is not trained !!")
      throw ClassifierException("Trying to train no supervised model",classifierControllerTrainModelNotSupervisedModel)

    }else {
      evaluateModel(model)
    }
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
        val modeltrained:NaiveBayesModel = if(status != "trained"){
          bayesClassifier.trainModel(partition)(sc)
        } else{
          val path = config.getString("models.path") +s"bayes_${model._id.value}"
          NaiveBayesModel.load(sc, path)
        }

        val modelResult =
          bayesClassifier.evaluateModel(partition)(sc)
        dataContext.executionDAO.update(copy(status= "trained"))
        dataContext.modelResultDAO.create(modelResult)
        Future(modelResult)
      case BOOSTING =>
        val boostModel = model.asInstanceOf[GradientBoostingClassifier]
        val partition = boostModel.prepareModel()(sc)
        val modeltrained:GradientBoostedTreesModel = if(status != "trained"){
          boostModel.trainModel(partition)(sc)
        } else{
          val path = config.getString("models.path") +s"gradient_${model._id.value}"
          GradientBoostedTreesModel.load(sc, path)
        }
        val modelResult =
          boostModel.evaluateModel(partition)(sc)
        dataContext.executionDAO.update(copy(status= "trained"))
        dataContext.modelResultDAO.create(modelResult)
        Future(modelResult)
      case RR =>
        val boostModel = model.asInstanceOf[RandomForestClassifier]
        val partition = boostModel.prepareModel()(sc)
        if(status != "trained"){
          boostModel.trainModel(partition)(sc)
        }
        val modelResult =
          boostModel.evaluateModel(partition)(sc)
        dataContext.executionDAO.update(copy(status= "trained"))
        dataContext.modelResultDAO.create(modelResult)
        Future(modelResult)
      case _ =>
        logger.warn(s"This kind of Model ${model.type_classifier} is not supervised !!")
        throw ClassifierException("Trying to train no supervised model",classifierControllerTrainModelNotSupervisedModel)
    }
  }


  def runModel(tweets:RDD[TweetInfo], model:ModelClassifier)(implicit sc: SparkContext):Future[List[TweetResult]] = {
   /* tweets.map{tweet   =>
      logger.info(s"[EXECUTION] $modelId STARTING EXECUTION ")

    } */
   val startTimeMillis = System.currentTimeMillis()

   val result = model.type_classifier match {
      case BAYES =>
        val bayesClas = model.asInstanceOf[BayesClassifier]
        val partition = bayesClas.prepareModel()(sc)
        val modelResult =
          bayesClas.runModel(this._id.get,partition)(sc)
        val (pos,neu,neg) =if (bayesClas.type_model == "bernoulli") {
          val pos =modelResult.map(_.result == 1).count()
          val neg =modelResult.map(_.result == 0).count()
          (pos, 0L, neg)
        }else {
          val pos =modelResult.filter(_.result == 2.0).count()
          val neg =modelResult.filter(_.result == 0.0).count()
          val neu = modelResult.filter(_.result == 1.0).count()
          (pos, neu, neg)
        }
        val nAggs = ResultsAggs(Id.generate, topicId,ModelConverter.modelConverter.toView(model),pos, neg,neu)
        dataContext.aggsDAO.create(nAggs)
        Future(modelResult.collect().toList)

      case BOOSTING =>
        val boostModel = model.asInstanceOf[GradientBoostingClassifier]
        val partition = boostModel.prepareModel()(sc)
        val pathModels = config.getString("models.path") +s"gradient_${boostModel._id.value}"
        val modelTrained = GradientBoostedTreesModel.load(sc, pathModels)

        val modelResult =
          boostModel.runModel(_id.get,topicId,partition)(sc)
        val (pos,neu,neg) = {
          val pos =modelResult.filter(res =>(res.result > 1.0)).count()
          val neg =modelResult.filter(res =>(res.result <= 1.0)).count()
          (pos, 0L, neg)
        }
        val nAggs = ResultsAggs(Id.generate, topicId,ModelConverter.modelConverter.toView(model),pos, neg,neu)
        dataContext.aggsDAO.create(nAggs)
        Future(modelResult.collect().toList)
      case NLP =>
        val nlp = model.asInstanceOf[NLPStanfordClassifier]
        val modelResult =
          nlp.runModel(_id.get)(sc)
        val (pos,neu,neg) = {
          val pos =modelResult.filter(_.result >0.6).count()
          val neg =modelResult.filter(_.result < 0.4).count()
          val neu = modelResult.filter(dat => (dat.result <= 0.6 && dat.result >= 0.4)).count()
          (pos, neu, neg)
        }
        val nAggs = ResultsAggs(Id.generate, topicId,ModelConverter.modelConverter.toView(model),pos, neg,neu)
        dataContext.aggsDAO.create(nAggs)
        Future(modelResult.collect().toList)
      case VADER =>
        val nlp = model.asInstanceOf[VaderClassifier]
        val modelResult =
          nlp.runModel(_id.get)(sc)
        val (pos,neu,neg) = {
          val pos =modelResult.filter(_.result >0.5).count()
          val neg =modelResult.filter(_.result <= -0.5).count()
          val neu = modelResult.filter(dat =>  ((dat.result  > -0.5) && (dat.result  < 0.5))).count()
          (pos, neu, neg)
        }
        val nAggs = ResultsAggs(Id.generate, topicId,ModelConverter.modelConverter.toView(model),pos, neg,neu)
        dataContext.aggsDAO.create(nAggs)
        Future(modelResult.collect().toList)
      case RR =>
        val boostModel = model.asInstanceOf[RandomForestClassifier]
        val partition = boostModel.prepareModel()(sc)
        val pathModels = config.getString("models.path") +s"randomforest_${boostModel._id.value}"
        val modelTrained = RandomForestModel.load(sc, pathModels)
        val modelResult =
          boostModel.runModel(_id.get,topicId,partition)(sc)
        val (pos,neu,neg) = {
          val pos =modelResult.filter(res =>(boostModel.scoreToClass(res.result)) == 2.0).count()
          val neg =modelResult.filter(res =>(boostModel.scoreToClass(res.result)) == 0.0).count()
          val neu =modelResult.filter(res =>(boostModel.scoreToClass(res.result)) == 1.0).count()
          (pos, neu, neg)
        }
        val nAggs = ResultsAggs(Id.generate, topicId,ModelConverter.modelConverter.toView(model),pos, neg,neu)
        dataContext.aggsDAO.create(nAggs)
        Future(modelResult.collect().toList)

      case EMOJI =>
        val nlp = model.asInstanceOf[EmojiClassifier]
        val modelResult =
          nlp.runModel(_id.get)(sc)
        val (pos,neu,neg) = {
          val pos =modelResult.filter(_.result >0.6).count()
          val neg =modelResult.filter(_.result < 0.4).count()
          val neu = modelResult.filter(dat => (dat.result <= 0.6 && dat.result >= 0.4)).count()
          (pos, neu, neg)
        }
        val nAggs = ResultsAggs(Id.generate, topicId,ModelConverter.modelConverter.toView(model),pos, neg,neu)
        dataContext.aggsDAO.create(nAggs)
        Future(modelResult.collect().toList)
      case API =>
        val nlp = model.asInstanceOf[ApiClassifier]
        val partition = nlp.prepareModel()(sc)
        val modelResult =
          nlp.runModel(_id.get)(sc)
        modelResult
    }
    val endTimeMillis = System.currentTimeMillis()
    val durationSeconds = (endTimeMillis - startTimeMillis) / 1000
    logger.info(s"[EXECUTION-MODEL] Time of " +
      s"running of classifier ${model._id}  of type ${model.type_classifier}" +
      s"is $durationSeconds seconds")
    result
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
    val count= rdd.count()
    rdd
  }

}
