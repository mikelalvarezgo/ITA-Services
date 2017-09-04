import org.apache.spark.{SparkConf, SparkContext}

/**
  * Created by mikelwyred on 03/09/2017.
  */
object test  extends  App{

  //start a new HTTP server on port 8080 with apiActor as the handler
  val data = Array(1, 2, 3, 4, 5)

  val conf = new SparkConf().setMaster("local").setAppName("prueba")

  // val sourceTweets  = Source.actorPublisher[TweetInfo](,TweetInfo))
  implicit val sc = new SparkContext(conf)

  val rdd = sc.parallelize(data)

  val int = rdd.count()

  println(s"RDD have $int elements")
  sc.stop()

}
