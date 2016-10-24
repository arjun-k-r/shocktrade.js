package com.shocktrade.common.models.post

import com.shocktrade.common.models.user.UserLike
import org.scalajs.nodejs.util.ScalaJsHelper._

import scala.scalajs.js
import scala.scalajs.js.annotation.ScalaJSDefined

/**
  * Represents a comment
  * @author lawrence.daniels@gmail.com
  */
@ScalaJSDefined
class Comment extends js.Object {
  var _id: js.UndefOr[String] = js.undefined
  var text: js.UndefOr[String] = js.undefined
  var submitter: js.UndefOr[UserLike] = js.undefined
  var likes: js.UndefOr[Int] = js.undefined
  var likedBy: js.UndefOr[js.Array[String]] = js.undefined
  var replies: js.UndefOr[js.Array[Reply]] = js.undefined
  var creationTime: js.UndefOr[js.Date] = js.undefined
  var lastUpdateTime: js.UndefOr[js.Date] = js.undefined

  // UI-only indicators
  var likeLoading: js.UndefOr[Boolean] = js.undefined
  var newReply: js.UndefOr[Boolean] = js.undefined
}

/**
  * Comment Companion
  * @author lawrence.daniels@gmail.com
  */
object Comment {

  def apply(text: String, submitter: UserLike) = {
    val comment = new Comment()
    comment.text = text
    comment.submitter = submitter
    comment.creationTime = new js.Date()
    comment.likes = 0
    comment.likedBy = emptyArray[String]
    comment
  }

}
