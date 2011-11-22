/*
 * Copyright (c) 2011, Florian Lukas
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer. 2. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package de.fau.cooja.plugins.coojatrace



import reactive._
import scala.util.DynamicVariable



/**
 * Methods for creating a "MagicSignal".
 *
 * "Magic" signals are created automatically by implicit wraps and unwraps to simplify
 * test code because operations can be done on signals as they could be on their raw values.
 * 
 * '''Example:'''
 * {{{ intSignal*5 + stringSignal.length }}}
 *  can be written instead of 
 * {{{ for(s <- stringSignal; i <- intSignal) yield(i*5 + s.length) }}}
 */
package object magicsignals {
  /**
   * Implicitly wrap an expression into a signal of the expressions' result type. Every signal
   * which is implicitly unwrapped will be added as a dependency for this new signal so that
   * dependency value changes are correctly updated.
   *
   * '''Note:''' this method is called automatically by the compiler if an operation which is only
   * implemented for the value of a signal is called on the signal itself.
   *
   * @param f named parameter (function) which is to be turned into a signal
   * @param deplog [[de.fau.cooja.plugins.coojatrace.magicsignals.DynDepLog]] which will be pointed 
   *   to a new [[de.fau.cooja.plugins.coojatrace.magicsignals.DepLogger]]
   * @return [[Signal]] created by reapplying f at every change of the dependency signals
   * @tparam T result type of f / type of newly created signal
   */
  implicit def wrap[T](f: => T)(implicit deplog: DynamicDepLogger): Signal[T] = {
    // create a new DepLogger to track dependencies
    val deps = collection.mutable.ListBuffer[Signal[_]]()
    val deplogger = new DepLogger {
      def addDependency(s: Signal[_]) { deps.append(s) }
    }

    // evaluate f with the global deplog pointing to our new deplogger
    deplog.withValue(deplogger) {
      val v0 = f
    }

    
    if(deps.isEmpty) {
      // no dependencies found? return a Val but warn as well
      val logger = org.apache.log4j.Logger.getLogger(this.getClass)
      logger.warn("no dependencies found when wrapping value " + f + ", creating Val instead")
      Val(f)
    } else {
      // flatmap all dependency signals into one new signal, whose value is computed
      // by reevaluating f at every change
      deps.tail.foldLeft(deps.head.map(s => f)) {
        case (combined, signal) => signal.flatMap(s => combined)
      }
    }
  }

  /**
   * Implicitly unwrap a signal to its value and save this signal as a dependency
   * for the current magicsignal wrap operation.
   *
   * '''Note:''' this method is called automatically by the compiler if an operation which is only
   * implemented for the value of a signal is called on the signal itself.
   *
   * @param s [[Signal]] which is unwrapped
   * @param deplog [[de.fau.cooja.plugins.coojatrace.magicsignals.DynDepLog]] which points to
   *   the currently used [[DepLogger]]
   * @return current value of signal
   * @tparam T type of signal value
   */
  implicit def unwrap[T](s: Signal[T])(implicit deplog: DynamicDepLogger): T = {
    // add to dependency list
    deplog.value.addDependency(s)

    // return current value
    s.now
  }

  /**
   * Wrap a signal for use of === operator.
   * @param s Signal
   * @return ComparableSignal
   */
  implicit def Signal2ComparableSignal[T](s: Signal[T]): ComparableSignal[T] = new ComparableSignal(s)
}



package magicsignals {

/**
 * Tracks dependencies for a MagicSignal.
 */
trait DepLogger {
  /**
   * Add a dependency signal to list.
   * @param s dependency signal
   */
  def addDependency(s: Signal[_])
}



/**
 * Dynamic variable which points to the current [[de.fau.cooja.plugins.coojatrace.magicsignals.DepLogger]].
 */
class DynamicDepLogger extends DynamicVariable[DepLogger](new DepLogger {
  def addDependency(s: Signal[_]) {} // no dependency tracking when simulating
})



/**
 * Wrapper for signal which implements === operator for value comparisons.
 * 
 * @param signal [[Signal]] to compare
 * @tparam T type of signal value
 */
class ComparableSignal[T](signal: Signal[T]) {
  /**
   * Boolean signal which compares values of this and other specified signal. 
   * @param other signal to compare this signal to
   * @tparam X type of value of other signal
   * @return Signal[Boolean] with result of value comparison
   */ 
  def ===[X](other: Signal[X]) = for(a <- signal; b <- other) yield (a == b)
}

} // package magicsignals
