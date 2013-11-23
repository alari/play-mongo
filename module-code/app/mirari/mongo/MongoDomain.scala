package mirari.mongo

import reactivemongo.bson.BSONObjectID
import org.joda.time.DateTime
import play.api.libs.json.{JsString, Json, JsValue}

/**
 * Mongo domain supertype
 *
 * @author alari
 * @since 8/1/13 12:24 AM
 */
trait MongoDomain[Id] {
  def _id: Option[Id]
  def id: String
  def hasId = _id.isDefined
}

object MongoDomain {

  /**
   * Domain with ObjectID _id
   */
  trait Oid extends MongoDomain[BSONObjectID]{
    def id = _id.map(_.stringify).getOrElse("")

    def idTimestamp = _id.map(_.time)

    def idDatetime = idTimestamp.map(new DateTime(_))
  }

  object Oid {
    type Id = Option[BSONObjectID]
  }

  /**
   * Domain with String _id -- generate and build it yourself!
   */
  trait Str extends MongoDomain[String] {
    def id = _id.getOrElse("")
  }

  object Str {
    type Id = Option[String]
  }
}