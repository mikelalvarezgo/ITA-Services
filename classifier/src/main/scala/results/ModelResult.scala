package results

import domain.Id

/**
  * Created by mikelwyred on 19/06/2017.
  */
case class ModelResult(
  _id: Id,
  model_id: Id,
  positive_good_classified: Double,
  positive_bad_classified: Double,
  negative_good_classified: Double,
  negative_bad_classified: Double){

}
