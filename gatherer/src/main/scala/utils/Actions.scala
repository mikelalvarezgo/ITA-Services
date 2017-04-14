package utils

import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.ReceiverInputDStream

trait Actions {
  _: Config =>

  type Content

  type Action = RDD[Content] => Unit

  private var actions: List[Action] = List()

  val ssc: StreamingContext

  val stream: ReceiverInputDStream[Content]

  def listen(): Unit = {
    actions.foreach(f =>
      stream.foreachRDD(rdd => f(rdd)))
    ssc.start()
  }

  def when(action: Action): Unit = {
    actions = actions :+ action
  }

}
