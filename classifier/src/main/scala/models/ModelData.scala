package models

import domain.Id


case class ModelData(
  _id:Option[Id],
  name: String,
  tag_tweets:String, //emoji or vader
  type_classifier:ModelType,
  parameters: Map[String,String],
  partitionConf: PartitionConf)