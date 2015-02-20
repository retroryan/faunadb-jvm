package com.faunadb.httpclient

import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.{ArrayNode, ObjectNode}
import com.faunadb._
import com.faunadb.query.{Expression, Response, SetDeserializerModifier}

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait PageTerm
case class Before(ref: String) extends PageTerm
case class After(ref: String) extends PageTerm

class FaunaClient(connection: Connection) {
  val json = connection.json
  val queryJson = json.copy()
  queryJson.registerModule(new SimpleModule().setDeserializerModifier(new SetDeserializerModifier))

  def oldQuery(query: String): Future[FaunaSet] = {
    connection.get("/queries", "q" -> query).map { resourceToSet(_) }
  }

  def oldQuery(query: String, size: Int): Future[FaunaSet] = {
    connection.get("/queries", "q" -> query, "size" -> size.toString).map { resourceToSet(_) }
  }

  def oldQuery(query: String, size: Int, pageTerm: PageTerm): Future[FaunaSet] = {
    val pageTermPair = pageTerm match {
      case Before(ref) => ("before" -> ref)
      case After(ref) => ("after" -> ref)
    }
    connection.get("/queries", "q" -> query, "size" -> size.toString, pageTermPair).map { resourceToSet(_) }
  }

  def query[R <: Response](expr: Expression)(implicit t: reflect.ClassTag[R]): Future[R] = {
    val body = queryJson.createObjectNode()
    body.set("q", queryJson.valueToTree(expr))
    connection.post("/", body).map { resp =>
      queryJson.treeToValue(resp.resource, t.runtimeClass).asInstanceOf[R]
    }
  }

  private def postOrPut(instance: FaunaInstance) = {
    val body = json.createObjectNode()
    body.set("data", instance.data)
    if (instance.ref.isEmpty) {
      val uri = "/" + instance.classRef
      connection.post(uri, body)
    } else {
      val uri = "/" + instance.ref
      connection.put(uri, body)
    }
  }

  def createOrReplaceInstance(instance: FaunaInstance): Future[FaunaInstance] = {
    postOrPut(instance) map { resp =>
      json.treeToValue(resp.resource, classOf[FaunaInstance])
    }
  }

  def createOrPatchInstance(instance: FaunaInstance): Future[FaunaInstance] = {
    val body = json.createObjectNode()
    body.set("data", instance.data)
    val rv = if (instance.ref.isEmpty) {
      val uri = "/" + instance.classRef
      connection.post(uri, body)
    } else {
      val uri = "/" + instance.ref
      connection.patch(uri, body)
    }

    rv map { resp => json.treeToValue(resp.resource, classOf[FaunaInstance]) }
  }

  def createDatabase(dbName: String): Future[FaunaDatabase] = {
    val instance = new FaunaInstance(ref="databases/"+dbName, classRef="databases")
    postOrPut(instance) map { resp =>
      json.treeToValue(resp.resource, classOf[FaunaDatabase])
    }
  }

  def createClass(classRef: String): Future[FaunaClass] = {
    val instance = new FaunaInstance(ref=classRef, classRef="classes")
    postOrPut(instance) map { resp =>
      json.treeToValue(resp.resource, classOf[FaunaClass])
    }
  }

  def createKey(database: String, role: String): Future[FaunaKey] = {
    val body = json.createObjectNode()
    body.put("database", database)
    body.put("role", role)
    connection.post("/keys", body) map { resp =>
      json.treeToValue(resp.resource, classOf[FaunaKey])
    }
  }

  def createIndex(indexRef: String, sourceRef: String, path: String, unique: Boolean): Future[FaunaIndex] = {
    val body = json.createObjectNode()
    body.put("source", sourceRef)
    body.put("path", path)
    body.put("unique", unique)
    connection.put(indexRef, body).map { resp =>
      json.treeToValue(resp.resource, classOf[FaunaIndex])
    }
  }

  def findInstance(instanceRef: String): Future[Option[FaunaInstance]] = {
    connection.get(instanceRef)
      .map { resp => Some(json.treeToValue(resp.resource, classOf[FaunaInstance])) }
      .recover { case _: NotFoundException => None }
  }

  private def resourceToSet(resp: ResourceResponse): FaunaSet  = {
    val resources = resp.resource.asInstanceOf[ObjectNode].get("resources")
    resp.resource.asInstanceOf[ObjectNode].remove("resources")
    val rv = json.treeToValue(resp.resource, classOf[FaunaSet])

    if (resources.isArray && resp.references.size() > 0) {
      val resArray = resources.asInstanceOf[ArrayNode]
      resArray foreach { ref =>
        val refStr = ref.asText()
        rv.resources += (refStr -> json.treeToValue(resp.references.get(refStr), classOf[FaunaInstance]))
      }
    }

    rv
  }
}