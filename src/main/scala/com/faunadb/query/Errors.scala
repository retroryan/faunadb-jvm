package com.faunadb.query

import java.util.AbstractMap.SimpleImmutableEntry
import java.util.{Map => JMap, Optional}

import com.fasterxml.jackson.annotation.JsonProperty

import scala.collection.JavaConverters._

object Errors {
  val ValidationFailed = "validation failed"
}

object ValidationError {
  case class Error(error: String)
  val DuplicateValue = Error("duplicate value")
}

case class ValidationError(error: String, reason: String)

sealed trait Error {
  val position: Seq[String]
  val code: String
  val reason: String

  @JsonProperty("code") def getCode() = code
  @JsonProperty("position") def getPosition() = position.asJava
  @JsonProperty("reason") def getReason() = reason
}

object Error {
  case class ValidationFailed(position: Seq[String], code: String, reason: String, parameters: scala.collection.SortedMap[String, ValidationError]) extends Error {
      def getValidationError(err: ValidationError.Error): Optional[JMap.Entry[String, ValidationError]] = {
      parameters.find { case (field, error) =>  error.error == err.error }
        .map { case (k,v) => new SimpleImmutableEntry[String, ValidationError](k,v) }
        .map(e => Optional.of(e.asInstanceOf[JMap.Entry[String, ValidationError]])).getOrElse(Optional.empty[JMap.Entry[String, ValidationError]]())
    }

    def getParameters() = parameters.asJava
  }

  case class UnknownError(position: Seq[String], code: String, reason: String) extends Error
}
