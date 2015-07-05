package com.shocktrade.javascript.directives

import com.ldaniels528.scalascript.core.{Attributes, JQLite}
import com.ldaniels528.scalascript.{Directive, Scope}
import org.scalajs.dom.console

import scala.language.postfixOps
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{literal => JS}
import scala.util.Try

/**
 * Change Arrow Directive
 * @author lawrence.daniels@gmail.com
 * @example <change-arrow value="{{ q.change }}"></change-arrow>
 */
class ChangeArrowDirective extends Directive[ChangeArrowDirectiveScope] {
  override val restrict = "E"
  override val scope = JS(value = "@value")
  override val transclude = true
  override val replace = false
  override val template = """<i ng-class="icon"></i>"""

  override def link(scope: ChangeArrowDirectiveScope, element: JQLite, attrs: Attributes) = {
    scope.$watch("value", { (newValue: js.UndefOr[Any], oldValue: js.UndefOr[Any]) =>
      scope.icon = newValue.toOption flatMap getNumericValue map {
        case v if v > 0 => "fa fa-arrow-up positive"
        case v if v < 0 => "fa fa-arrow-down negative"
        case _ => "fa fa-minus null"
      } orNull
    })
  }

  private def getNumericValue(newValue: Any): Option[Double] = {
    console.log(s"getNumericValue: newValue = $newValue")
    newValue match {
      case n: Number => Some(n.doubleValue)
      case s: String if s.nonEmpty => Try(s.toDouble).toOption
      case _ => None
    }
  }
}

/**
 * Change Arrow Directive Scope
 * @author lawrence.daniels@gmail.com
 */
trait ChangeArrowDirectiveScope extends Scope {
  var value: js.UndefOr[Any] = js.native
  var icon: String = js.native

}

/**
 * Change Arrow Directive Scope Singleton
 * @author lawrence.daniels@gmail.com
 */
object ChangeArrowDirectiveScope {

  def apply(): ChangeArrowDirectiveScope = {
    val scope = new js.Object().asInstanceOf[ChangeArrowDirectiveScope]
    scope.icon = null
    scope
  }

}

