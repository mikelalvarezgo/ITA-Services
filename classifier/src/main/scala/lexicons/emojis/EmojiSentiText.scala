package lexicons.emojis

import lexicons.utils.ResourceUtils
import utils.Config

import scala.collection.{Map, Seq}

/**
  * Created by mikelwyred on 05/09/2017.
  */
object EmojiSentiText extends  Config{

    val EMOJI_LEXICON_PATH = config.getString("lexicon.emoji_path")
    val  WORD_LEXICON_PATH= config.getString("lexicon.word_path")

    def makeLexDict(lexiconFile: Seq[String]) : Map[String, Double] = {
      var result = Map[String, Double]()

      for (line <- lexiconFile) {
        val lineArray = line.trim().split('\t')
        result += (lineArray(0) -> lineArray(1).toDouble)
      }

    result
  }

  val emojiLexicon =
    makeLexDict(ResourceUtils.readFileAsList(EMOJI_LEXICON_PATH))
  val wordLexicon =
    makeLexDict(ResourceUtils.readFileAsList(WORD_LEXICON_PATH))


  def textPolarity(text:String):Double = {
    def emojiPolarity(text: String): Double = {
      0.0
    }

    def wordPolarity(text: String): Double = {
      0.0

    }
    0.0
  }

}
