package com.ita.classifier.models

import com.ita.domain.Id


case class ModelData(
  _id:Option[Id],
  name: String,
  tag_tweets:String, //emoji or vader
  type_classifier:ModelType,
  parameters: Map[String,String],
  partitionConf: Option[PartitionConf])