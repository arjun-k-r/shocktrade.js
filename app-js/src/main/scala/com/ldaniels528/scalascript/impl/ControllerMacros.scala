// -   Project: scalajs-scalascript (https://github.com/jokade/scalajs-scalascript)
// Description: Macro-based enhancements for Angular controllers
//
// Distributed under the MIT License (see included file LICENSE)
package com.ldaniels528.scalascript.impl

import acyclic.file
import com.ldaniels528.scalascript._

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

// TODO: understand Scala macros and clean up this hacked mess ...
protected[scalascript] class ControllerMacros(val c: Context) extends MacroBase with ControllerMacroUtils {
  import c.universe._

  // print generated code to console during compilation
  private lazy val logCode = c.settings.exists( _ == "com.ldaniels528.scalascript.ControllerMacros.debug" )

  /* type definitions */
  val scopeController = typeOf[ScopeController]

  def controllerOf[T <: NGController: c.WeakTypeTag] = {
    val controllerType = weakTypeOf[T]
    val name = controllerType.toString
    if( controllerType <:< scopeController)
      createScopeController(controllerType, q"$name")
    else
      createController(controllerType, q"$name")
  }

  def controllerOfWithName[T <: NGController: c.WeakTypeTag](name: c.Expr[String]) = {
    val controllerType = weakTypeOf[T]
    if( controllerType <:< scopeController)
      createScopeController(controllerType, q"$name")
    else
      createController(controllerType, q"$name")
  }



  private def createScopeController(ct: Type, name: Tree) = {
    // print debug information at runtime if runtimeLogging==true
    val debug =
      if(runtimeLogging)
        q"""global.console.debug("Created Controller "+$name, ctrl.asInstanceOf[js.Dynamic])"""
      else q"()"

    val postConstruction = q"""$debug"""

    val module = Select(c.prefix.tree, TermName("self"))

    // ctrlDeps: the list of dependencies required by the controller constructor
    // ctrlArgs: list of arguments required by the controller constructor
    // ctrlDepNames: list with names of the dependencies to be injected
    val cm = getConstructor(ct)
    val (ctrlDeps,ctrlArgs) = makeArgsList(cm)
    val ctrlDepNames = getDINames(cm)

    // AngularJS controller construction array
    val constructor = q"""js.Array[Any](..$ctrlDepNames,
          ((..$ctrlDeps) => {
            val ctrl = new $ct(..$ctrlArgs)
            $postConstruction
            ctrl
          }):js.Function)"""

    // controller registration
    val tree =
      q"""{import scala.scalajs.js
           import js.Dynamic.{global,literal}
           $module.controller($name,$constructor)
          }"""

    if(logCode) printCode( tree )
    tree

  }


  private def createController(ct: Type, name: Tree) = {
    // print debug information at runtime if runtimeLogging==true
    val debug =
      if(runtimeLogging)
        q"""global.console.debug("Created Controller "+$name, ctrl.asInstanceOf[js.Dynamic], "with scope:", scope)"""
      else q"()"

    val postConstruction = q"""..${copyMembers(ct)}
                               $debug"""

    val module = Select(c.prefix.tree, TermName("self"))
    // ctrlDeps: the list of dependencies required by the controller constructor
    // ctrlArgs: list of arguments required by the controller constructor
    // ctrlDepNames: list with names of the dependencies to be injected
    val cm = getConstructor(ct)
    val (ctrlDeps,ctrlArgs) = makeArgsList(cm)
    val ctrlDepNames = getDINames(cm)

    // AngularJS controller construction array
    val constructor = q"""js.Array[Any](..$ctrlDepNames,
          ((scope:js.Dynamic, ..$ctrlDeps) => {
            val ctrl = new $ct(..$ctrlArgs)
            $postConstruction
            scope
          }):js.ThisFunction)"""

    // controller registration
    val tree =
      q"""{import scala.scalajs.js
           import js.Dynamic.{global,literal}
           $module.controller($name,$constructor)
          }"""

    if(logCode) printCode( tree )
    tree
  }


}


protected[scalascript] trait ControllerMacroUtils {
  this: MacroBase =>
  import c.universe._


  protected def copyMembers(ct: Type) = {
    def isGetter(s: MethodSymbol) : Boolean = s.isGetter || s.paramLists.isEmpty
    def isSetter(s: MethodSymbol) : Boolean = s.isSetter || s.name.toString.endsWith("_$eq")
    def isFunc(s: MethodSymbol) : Boolean = !(isGetter(s) || isSetter(s))

    val props = ct.decls.filter( p => p.isPublic && p.isMethod && !p.isConstructor).map( _.asMethod )
    val funcs = props.filter( isFunc )
    val getters = props.filter(isGetter)
    val setters = props.filter(isSetter).map{ s=>
      val name = s.name.toString
      val getterName = name.substring(0,name.length-4)
      getterName -> s
    }.toMap

    (getters map { getter =>
      val getterName = getter.name.toString
      val setterOption = setters.get(getterName).map{ setter =>
        val setterType = setter.paramLists.head.head.typeSignature
        q"""global.Object.defineProperty(scope,$getterName,
              literal(get = ()=>ctrl.$getter,
                      set = (v:$setterType) => ctrl.$getter = v))"""
      }
      setterOption.getOrElse {
        q"""global.Object.defineProperty(scope,$getterName,literal(get = ()=>ctrl.$getter))"""
      }
    }) ++
      (funcs map { func =>
        val funcName = func.name.toString
        val (params,args) = makeArgsList(func)
          q"""global.Object.defineProperty(scope,$funcName,literal(value = (..$params) => ctrl.$func(..$args)))"""
      })
  }

}
