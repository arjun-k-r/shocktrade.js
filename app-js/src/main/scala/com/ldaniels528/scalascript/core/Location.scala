// -   Project: scalajs-scalascript (https://github.com/jokade/scalajs-scalascript)
// Description: API for Angular $location
//
// Distributed under the MIT License (see included file LICENSE)
package com.ldaniels528.scalascript.core

import acyclic.file
import scala.scalajs.js

/**
 * Defines the bindings to the \$location service.
 *
 * @see [[https://docs.angularjs.org/api/ng/service/\$location]]
 */
trait Location extends js.Object with ProvidedService {

  def absUrl(): String = js.native
  def url(url: String = null, replace: String = null): String = js.native
  def protocol(): String = js.native
  def host(): String = js.native
  def port(): Int = js.native
  def path(): String = js.native
  def path(path: String): Location = js.native
  def search() : js.Object = js.native
  def search(search: js.Any, paramValue: js.Any = null) : Location = js.native
  def hash(hash: String = null): String = js.native
  def replace(): Unit = js.native
}
