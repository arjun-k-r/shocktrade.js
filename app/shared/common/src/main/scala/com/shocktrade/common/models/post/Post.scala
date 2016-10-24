package com.shocktrade.common.models.post

import com.shocktrade.common.models.user.User
import org.scalajs.nodejs.util.ScalaJsHelper._
import org.scalajs.sjs.JsUnderOrHelper._

import scala.scalajs.js
import scala.scalajs.js.annotation.ScalaJSDefined

/**
  * Represents a Post model object
  * @author lawrence.daniels@gmail.com
  */
@ScalaJSDefined
class Post(var _id: js.UndefOr[String] = js.undefined,
           var text: js.UndefOr[String] = js.undefined,
           var submitter: js.UndefOr[User] = js.undefined,
           var submitterId: js.UndefOr[String] = js.undefined,
           var summary: js.UndefOr[SharedContent] = js.undefined,
           var likes: js.UndefOr[Int] = js.undefined,
           var likedBy: js.UndefOr[js.Array[String]] = js.undefined,
           var creationTime: js.Date = new js.Date(),
           var lastUpdateTime: js.Date = new js.Date(),

           // collections
           var attachments: js.UndefOr[js.Array[String]] = js.undefined,
           var comments: js.UndefOr[js.Array[Comment]] = js.undefined,
           var replyLikes: js.UndefOr[js.Array[ReplyLikes]] = js.undefined,
           var tags: js.UndefOr[js.Array[String]] = js.undefined,

           // Angular-specific properties
           var loading: js.UndefOr[Boolean] = js.undefined,
           var deleteLoading: js.UndefOr[Boolean] = js.undefined,
           var likeLoading: js.UndefOr[Boolean] = js.undefined,
           var newComment: js.UndefOr[Boolean] = js.undefined,
           var refreshLoading: js.UndefOr[Boolean] = js.undefined,
           var summaryLoaded: js.UndefOr[Boolean] = js.undefined,
           var summaryLoadQueued: js.UndefOr[Boolean] = js.undefined) extends PostLike

/**
  * Post Companion
  * @author lawrence.daniels@gmail.com
  */
object Post {

  def apply(user: User): Post = {
    val post = new Post()
    post.creationTime = new js.Date()
    post.lastUpdateTime = new js.Date()
    post.submitter = user
    post.submitterId = user._id
    post.text = ""
    post.likes = 0
    post.attachments = emptyArray[String]
    post.comments = emptyArray[Comment]
    post.likedBy = emptyArray[String]
    post.replyLikes = emptyArray[ReplyLikes]
    post.tags = emptyArray[String]
    post
  }

  /**
    * Post Enrichment
    * @param post the given [[Post post]]
    */
  implicit class PostEnrichment(val post: Post) extends AnyVal {

    @inline
    def copy(_id: js.UndefOr[String] = js.undefined,
             text: js.UndefOr[String] = js.undefined,
             submitter: js.UndefOr[User] = js.undefined,
             submitterId: js.UndefOr[String] = js.undefined,
             summary: js.UndefOr[SharedContent] = js.undefined,
             likes: js.UndefOr[Int] = js.undefined,
             likedBy: js.UndefOr[js.Array[String]] = js.undefined,
             creationTime: js.UndefOr[js.Date] = js.undefined,
             lastUpdateTime: js.UndefOr[js.Date] = js.undefined,
             // collections
             attachments: js.UndefOr[js.Array[String]] = js.undefined,
             comments: js.UndefOr[js.Array[Comment]] = js.undefined,
             replyLikes: js.UndefOr[js.Array[ReplyLikes]] = js.undefined,
             tags: js.UndefOr[js.Array[String]] = js.undefined,
             // Angular-specific properties
             loading: js.UndefOr[Boolean] = js.undefined,
             likeLoading: js.UndefOr[Boolean] = js.undefined,
             newComment: js.UndefOr[Boolean] = js.undefined,
             refreshLoading: js.UndefOr[Boolean] = js.undefined,
             summaryLoaded: js.UndefOr[Boolean] = js.undefined,
             summaryLoadQueued: js.UndefOr[Boolean] = js.undefined) = {
      new Post(
        _id = _id ?? post._id,
        text = text ?? post.text,
        submitter = submitter ?? post.submitter,
        submitterId = submitterId ?? post.submitterId,
        summary = summary ?? post.summary,
        likes = likes ?? post.likes,
        likedBy = likedBy ?? post.likedBy,
        creationTime = creationTime.getOrElse(new js.Date()),
        lastUpdateTime = lastUpdateTime.getOrElse(new js.Date()),

        // collections
        attachments = attachments ?? post.attachments,
        comments = comments ?? post.comments,
        replyLikes = replyLikes ?? post.replyLikes,
        tags = tags ?? post.tags,

        // Angular-specific properties
        loading = loading ?? post.loading,
        likeLoading = likeLoading ?? post.likeLoading,
        newComment = newComment ?? post.newComment,
        refreshLoading = refreshLoading ?? post.refreshLoading,
        summaryLoaded = summaryLoaded ?? post.summaryLoaded,
        summaryLoadQueued = summaryLoadQueued ?? post.summaryLoadQueued
      )
    }

  }

}
