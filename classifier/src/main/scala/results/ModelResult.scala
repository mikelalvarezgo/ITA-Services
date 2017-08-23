package results

import domain.Id

/**
  * Created by mikelwyred on 19/06/2017.
  */
case class ModelResult(
  _id: Id,
  model_id: Id,
  train_positive_good_classified: Int,
  trainpositive_bad_classified: Int,
  trainnegative_good_classified: Int,
  trainnegative_bad_classified: Int,
  val_positive_good_classified: Int,
  val_positive_bad_classified: Int,
  val_negative_good_classified: Int,
  val_negative_bad_classified: Int){

}
