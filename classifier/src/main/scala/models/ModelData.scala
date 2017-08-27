package models

import domain.Id


case class ModelData(
  _id:Option[Id],
  name: String,
  listPositiveEmojis: List[String],
  listNegativeEmojis: List[String],
  type_classifier:ModelType,
  parameters: Map[String,String],
  partitionConf: PartitionConf)