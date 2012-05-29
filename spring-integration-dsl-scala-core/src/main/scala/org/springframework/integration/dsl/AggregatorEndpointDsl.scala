/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.integration.dsl

import org.springframework.integration.store.{ SimpleMessageStore, MessageStore }
import java.util.UUID
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.integration.aggregator.DefaultAggregatingMessageGroupProcessor
import org.springframework.integration.aggregator.AggregatingMessageHandler
import org.springframework.util.StringUtils
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.springframework.integration.dsl.utils.DslUtils

/**
 * This class provides DSL and related components to support "Message Aggregator" pattern
 *
 * @author Oleg Zhurakousky
 */
object aggregate {

  trait RestrictiveFunction[A, B]

  type NotUnitType[T] = RestrictiveFunction[T, Unit]

  implicit def nsub[A, B]: RestrictiveFunction[A, B] = null
  implicit def nsubAmbig1[A, B >: A]: RestrictiveFunction[A, B] = null
  implicit def nsubAmbig2[A, B >: A]: RestrictiveFunction[A, B] = null
  /**
   *
   */
  def apply() = new SendingEndpointComposition(null,
    new Aggregator)
  		with On
  		with Until
  		with ExpireGroupsOnCompletion
  		with AggregatorAttributes
  		with SendPartialResultOnExpiry
  		with KeepReleasedMessages
  /**
   *
   */
  def apply[T, R: NotUnitType](aggregationFunction: Function1[Iterable[_], R]) = new SendingEndpointComposition(null,
    new Aggregator(aggregationFunction = aggregationFunction))
  		with On
  		with Until
  		with ExpireGroupsOnCompletion
  		with AggregatorAttributes
  		with SendPartialResultOnExpiry
  		with KeepReleasedMessages

  /**
   *
   */
  def on[T, R: NotUnitType](correlationFunction: Function1[_, R]) = new SendingEndpointComposition(null,
    new Aggregator(correlationFunction = correlationFunction))
  			with ExpireGroupsOnCompletion
  			with AggregatorAttributes
  			with SendPartialResultOnExpiry
  			with KeepReleasedMessages {

    def until(releaseFunction: Function1[_, Boolean]) = new SendingEndpointComposition(null,
      new Aggregator(correlationFunction = correlationFunction, releaseFunction = releaseFunction))
    		with ExpireGroupsOnCompletion
    		with AggregatorAttributes
    		with SendPartialResultOnExpiry
    		with KeepReleasedMessages
  }
  /**
   *
   */
  def until(releaseFunction: Function1[_, Boolean]) = new SendingEndpointComposition(null,
    new Aggregator(releaseFunction = releaseFunction))
  			with ExpireGroupsOnCompletion
  			with AggregatorAttributes
  			with SendPartialResultOnExpiry
  			with KeepReleasedMessages {

  }

  def additionalAttributes(name: String = null,
    keepReleasedMessages: java.lang.Boolean = null,
    messageStore: MessageStore = null,
    sendPartialResultsOnExpiry: java.lang.Boolean = null,
    expireGroupsUponCompletion: java.lang.Boolean = null) =
    new SendingEndpointComposition(null, new Aggregator)
}

private[dsl] case class Aggregator(override val name: String = "$aggr_" + UUID.randomUUID().toString.substring(0, 8),
  val keepReleasedMessages: java.lang.Boolean = null,
  val sendPartialResultOnExpiry: java.lang.Boolean = null,
  val expireGroupsOnCompletion: java.lang.Boolean = null,
  val aggregationFunction: Function1[Iterable[_], _] = null,
  val correlationFunction: Function1[_, _] = null,
  val releaseFunction: Function1[_, Boolean] = null,
  val additionalAttributes: Map[String, _] = null) extends SimpleEndpoint(name, null) {

  override def build(document: Document,
    targetDefinitionFunction: Function1[Any, Tuple2[String, String]],
    compositionInitFunction: Function2[BaseIntegrationComposition, AbstractChannel, Unit],
    inputChannel: AbstractChannel,
    outputChannel: AbstractChannel): Element = {

    require(inputChannel != null, "'inputChannel' must be provided")

    val element = document.createElement("int:aggregator")
    element.setAttribute("id", this.name)
    element.setAttribute("input-channel", inputChannel.name);
    if (outputChannel != null) {
      element.setAttribute("output-channel", outputChannel.name);
    }
    element
  }
}

private[dsl] trait On {

  import aggregate._

  def on[T, R: NotUnitType](correlationFunction: Function1[_, R]) = new SendingEndpointComposition(null,
    new Aggregator) with ExpireGroupsOnCompletion with AggregatorAttributes with SendPartialResultOnExpiry with KeepReleasedMessages with Until {

    val currentAggregator = DslUtils.getTarget[Aggregator](this)
    require(currentAggregator != null, "Trait 'On' can only be applied on composition with existing Aggregator instance")
    val enrichedAggregator = currentAggregator.copy(correlationFunction = correlationFunction)
    new SendingEndpointComposition(null, enrichedAggregator)

  }
}

private[dsl] trait Until {
  /**
   *
   */
  def until(releaseFunction: Function1[_, Boolean]) = new SendingEndpointComposition(null,
    new Aggregator) with ExpireGroupsOnCompletion with SendPartialResultOnExpiry with AggregatorAttributes {

    val currentAggregator = DslUtils.getTarget[Aggregator](this)
    require(currentAggregator != null, "Trait 'Until' can only be applied on composition with existing Aggregator instance")
    val enrichedAggregator = currentAggregator.copy(releaseFunction = releaseFunction)
    new SendingEndpointComposition(null, enrichedAggregator)

  }
}

private[dsl] trait ExpireGroupsOnCompletion {
  def expireGroupsOnCompletion = new SendingEndpointComposition(null, new Aggregator) with AggregatorAttributes {

    val currentAggregator = DslUtils.getTarget[Aggregator](this)
    require(currentAggregator != null, "Trait 'ExpireGroupsOnCompletion' can only be applied on composition with existing Aggregator instance")
    val enrichedAggregator = currentAggregator.copy(expireGroupsOnCompletion = true)
    new SendingEndpointComposition(null, enrichedAggregator)

    def sendPartialResultOnExpiry = new SendingEndpointComposition(null, new Aggregator) with AggregatorAttributes {

      val currentAggregator = DslUtils.getTarget[Aggregator](this)
      val enrichedAggregator = currentAggregator.copy(sendPartialResultOnExpiry = true)
      new SendingEndpointComposition(null, enrichedAggregator)

      def keepReleasedMessages = new SendingEndpointComposition(null, new Aggregator) with AggregatorAttributes {
        val currentAggregator = DslUtils.getTarget[Aggregator](this)
        val enrichedAggregator = currentAggregator.copy(keepReleasedMessages = true)
        new SendingEndpointComposition(null, enrichedAggregator)
      }
    }

    def keepReleasedMessages = new SendingEndpointComposition(null, new Aggregator) with AggregatorAttributes {

      val currentAggregator = DslUtils.getTarget[Aggregator](this)
      val enrichedAggregator = currentAggregator.copy(keepReleasedMessages = true)
      new SendingEndpointComposition(null, enrichedAggregator)

      def sendPartialResultOnExpiry = new SendingEndpointComposition(null, new Aggregator) with AggregatorAttributes {
        val currentAggregator = DslUtils.getTarget[Aggregator](this)
        val enrichedAggregator = currentAggregator.copy(sendPartialResultOnExpiry = true)
        new SendingEndpointComposition(null, enrichedAggregator)
      }
    }
  }
}

private[dsl] trait SendPartialResultOnExpiry {
  def sendPartialResultOnExpiry = new SendingEndpointComposition(null, new Aggregator) with AggregatorAttributes {

    val currentAggregator = DslUtils.getTarget[Aggregator](this)
    require(currentAggregator != null, "Trait 'SendPartialResultOnExpiry' can only be applied on composition with existing Aggregator instance")
    val enrichedAggregator = currentAggregator.copy(sendPartialResultOnExpiry = true)
    new SendingEndpointComposition(null, enrichedAggregator)

    def expireGroupsOnCompletion = new SendingEndpointComposition(null, new Aggregator) with AggregatorAttributes {

      val currentAggregator = DslUtils.getTarget[Aggregator](this)
      val enrichedAggregator = currentAggregator.copy(expireGroupsOnCompletion = true)
      new SendingEndpointComposition(null, enrichedAggregator)

      def keepReleasedMessages = new SendingEndpointComposition(null, new Aggregator) with AggregatorAttributes {

        val currentAggregator = DslUtils.getTarget[Aggregator](this)
        val enrichedAggregator = currentAggregator.copy(keepReleasedMessages = true)
        new SendingEndpointComposition(null, enrichedAggregator)

      }
    }

    def keepReleasedMessages = new SendingEndpointComposition(null, new Aggregator) with AggregatorAttributes {

      val currentAggregator = DslUtils.getTarget[Aggregator](this)
      val enrichedAggregator = currentAggregator.copy(keepReleasedMessages = true)
      new SendingEndpointComposition(null, enrichedAggregator)

      def expireGroupsOnCompletion = new SendingEndpointComposition(null, new Aggregator) with AggregatorAttributes {

        val currentAggregator = DslUtils.getTarget[Aggregator](this)
        val enrichedAggregator = currentAggregator.copy(expireGroupsOnCompletion = true)
        new SendingEndpointComposition(null, enrichedAggregator)

      }
    }
  }
}

private[dsl] trait KeepReleasedMessages {

  def keepReleasedMessages = new SendingEndpointComposition(null, new Aggregator) with AggregatorAttributes {

    val currentAggregator = DslUtils.getTarget[Aggregator](this)
    require(currentAggregator != null, "Trait 'KeepReleasedMessages' can only be applied on composition with existing Aggregator instance")
    val enrichedAggregator = currentAggregator.copy(keepReleasedMessages = true)
    new SendingEndpointComposition(null, enrichedAggregator)

    def expireGroupsOnCompletion = new SendingEndpointComposition(null, new Aggregator) with AggregatorAttributes {

      val currentAggregator = DslUtils.getTarget[Aggregator](this)
      val enrichedAggregator = currentAggregator.copy(expireGroupsOnCompletion = true)
      new SendingEndpointComposition(null, enrichedAggregator)

      def sendPartialResultOnExpiry = new SendingEndpointComposition(null, new Aggregator) with AggregatorAttributes {

        val currentAggregator = DslUtils.getTarget[Aggregator](this)
        val enrichedAggregator = currentAggregator.copy(sendPartialResultOnExpiry = true)
        new SendingEndpointComposition(null, enrichedAggregator)

      }
    }

    def sendPartialResultOnExpiry = new SendingEndpointComposition(null, new Aggregator) with AggregatorAttributes {

      val currentAggregator = DslUtils.getTarget[Aggregator](this)
      val enrichedAggregator = currentAggregator.copy(sendPartialResultOnExpiry = true)
      new SendingEndpointComposition(null, enrichedAggregator)

      def expireGroupsOnCompletion = new SendingEndpointComposition(null, new Aggregator) with AggregatorAttributes {

        val currentAggregator = DslUtils.getTarget[Aggregator](this)
        val enrichedAggregator = currentAggregator.copy(expireGroupsOnCompletion = true)
        new SendingEndpointComposition(null, enrichedAggregator)

      }
    }
  }
}

private[dsl] trait AggregatorAttributes {
  def additionalAttributes(name: String = "$aggr_" + UUID.randomUUID().toString.substring(0, 8),
    messageStore: MessageStore = null) =

    new SendingEndpointComposition(null, new Aggregator(name = name, additionalAttributes = Map("messageStore" -> messageStore)))
}


