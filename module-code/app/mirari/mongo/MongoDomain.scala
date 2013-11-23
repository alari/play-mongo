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
trait MongoDomain {
  def id: String
}

object MongoDomain {

  /**
   * Domain with ObjectID _id
   */
  trait Oid extends MongoDomain{
    self: {def _id: Oid.Id}=>

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
  trait Str extends MongoDomain {
    self: {def _id: Str.Id}=>

    def id = _id.getOrElse("")
  }

  object Str {
    type Id = Option[String]
  }
}