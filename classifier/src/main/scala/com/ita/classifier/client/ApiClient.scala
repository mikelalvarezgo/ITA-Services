package client


import scala.concurrent.Future

/**
  * Created by mikelwyred on 15/08/2017.
  */

case class Probs(
  neg:Double,
  neutral:Double,
  pos: Double
)
case class ClientResponse(
  probability:Probs,
  label: String)  {
  def returnScore:Int = {
    if (probability.neg > probability.pos) {
      0
    }else {
      1
    }
  }
}
trait ApiClient {
  val base_url:String

  case class ClientPayLoad(
    text: String
  )
  def AnalizeText(textString:String):Future[ClientResponse]
}
