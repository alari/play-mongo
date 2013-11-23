package mirari.mongo

import reactivemongo.bson.BSONObjectID
import org.joda.time.DateTime

/**
 * @author alari
 * @since 8/1/13 12:24 AM
 */
trait MongoDomain {
  self: {def _id: MongoDomain.Id}=>

  def id = stringId

  def stringId: String = _id.map(_.stringify).getOrElse("")

  def idTimestamp = _id.map(_.time)

  def idTimestampDatetime = idTimestamp.map(new DateTime(_))
}

object MongoDomain {
  type Id = Option[BSONObjectID]
}