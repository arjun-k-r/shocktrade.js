package com.shocktrade.controlpanel.runtime.functions
package builtin

import com.shocktrade.controlpanel.runtime.{RuntimeContext, Scope, TextValue}
import org.scalajs.nodejs.request.Request
import org.scalajs.nodejs.util.ScalaJsHelper._

import scala.concurrent.ExecutionContext
import scala.scalajs.js.JSON

/**
  * daemons() Function
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
class DaemonsFx() extends Function {

  override def name = "daemons"

  override def params = Seq("remote")

  override def eval(rc: RuntimeContext, scope: Scope)(implicit ec: ExecutionContext) = {
    implicit val require = rc.require
    implicit val request = Request()

    val remote = scope.findVariable("remote").map(_.value.toString) getOrElse "localhost:1337"
    val promise = request.getFuture(s"http://$remote/api/daemons") map { case (response, data) => pretty(data) }
    promise.map(TextValue.apply)
  }

  private def pretty(data: String) = {
    JSON.dynamic.stringify(JSON.parse(data), null, "\t").asInstanceOf[String]
  }

}
