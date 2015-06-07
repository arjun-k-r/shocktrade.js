package com.ldaniels528.angularjs

import scala.scalajs.js

/**
 * Angular.js CookieStore
 * @author lawrence.daniels@gmail.com
 */
trait CookieStore extends js.Object {

  def get(key: String): js.UndefOr[String] = js.native

  def put[T](key: String, value: T): Unit = js.native

  def remove(key: String): String = js.native

}
