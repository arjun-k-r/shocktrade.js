package com.shocktrade.javascript.news

import org.scalajs.angularjs.Service
import org.scalajs.angularjs.http.Http
import org.scalajs.nodejs.util.ScalaJsHelper._

import scala.scalajs.js

/**
  * News Service
  * @author lawrence.daniels@gmail.com
  */
class NewsService($http: Http) extends Service {

  def getNewsSources = $http.get[js.Array[NewsSource]]("/api/news/sources")

  def getNewsFeed(feedId: String) = $http.get[js.Array[NewsChannel]](s"/api/news/feed/$feedId")

}

/**
  * News Channel
  * @author lawrence.daniels@gmail.com
  */
@js.native
trait NewsChannel extends js.Object {
  var items: js.Array[NewsItem]
}

/**
  * News Channel Companion Object
  * @author lawrence.daniels@gmail.com
  */
object NewsChannel {

  implicit class NewsChannelEnrichment(val channel: NewsChannel) extends AnyVal {

    def copy(items: js.UndefOr[js.Array[NewsItem]] = js.undefined) = {
      val newChannel = New[NewsChannel]
      newChannel.items = items getOrElse channel.items
      newChannel
    }
  }

}

/**
  * News Channel Item
  * @author lawrence.daniels@gmail.com
  */
@js.native
trait NewsItem extends js.Object {
  var description: String = js.native
  var quotes: js.Array[NewsQuote] = js.native
}

/**
  * News Channel Item Companion Object
  * @author lawrence.daniels@gmail.com
  */
object NewsItem {

  implicit class NewsItemEnrichment(val item: NewsItem) extends AnyVal {

    def copy(description: js.UndefOr[String] = js.undefined,
             quotes: js.UndefOr[js.Array[NewsQuote]] = js.undefined) = {
      val newItem = New[NewsItem]
      newItem.description = description getOrElse item.description
      newItem.quotes = quotes getOrElse item.quotes
      newItem
    }
  }

}

/**
  * News Quote
  * @author lawrence.daniels@gmail.com
  */
@js.native
trait NewsQuote extends js.Object {
  var name: js.UndefOr[String] = js.native
  var symbol: js.UndefOr[String] = js.native
  var exchange: js.UndefOr[String] = js.native
  var sector: js.UndefOr[String] = js.native
  var industry: js.UndefOr[String] = js.native
  var changePct: js.UndefOr[Double] = js.native
}
