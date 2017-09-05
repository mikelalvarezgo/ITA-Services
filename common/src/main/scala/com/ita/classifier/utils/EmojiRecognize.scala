package com.ita.classifier.utils

import com.vdurmont.emoji._
/**
  * Created by mikelwyred on 18/08/2017.
  */
object EmojiRecognize {

  val expresionRegular= "([\\u20a0-\\u32ff\\ud83c\\udc00-\\ud83d\\udeff\\udbb9\\udce5-\\udbb9\\udcee])";

    def StringToUnicode(value:String):String = EmojiParser.parseToUnicode(value)

  def containtTextEmojis(text: String): Boolean = {
    (text != EmojiParser.parseToAliases(text))
  }

  def extractEmojis(text:String): List[String] = ???

}
