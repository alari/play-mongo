package infra.mongo

import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import scala.util.Try
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsString

/**
 * @author alari
 * @since 8/1/13 12:43 AM
 */
trait MongoImplicits {
  implicit object BSONObjectIDFormat extends Format[BSONObjectID] {
    def writes(objectId: BSONObjectID): JsValue = objectIdFormat.writes(objectId.stringify)
    def reads(json: JsValue): JsResult[BSONObjectID] = objectIdFormat.reads(json) match {
      case JsSuccess(x, _) =>
        val maybeOID: Try[BSONObjectID] = BSONObjectID.parse(x)
        if(maybeOID.isSuccess) JsSuccess(maybeOID.get) else {
          JsError("Expected BSONObjectID as JsString")
        }
      case _ => JsError("Expected BSONObjectID as JsString")
    }
  }

  // Object ID formatters
  val objectIdFormat = OFormat[String](
    (__ \ "$oid").read[String],
    OWrites[String] {
      s => Json.obj("$oid" -> s)
    }
  )

  val toObjectId = OWrites[String] {
    s => Json.obj("_id" -> Json.obj("$oid" -> s))
  }

  val getObjectId = (__ \ '_id \ '$oid).json.pick[JsString]

  val fromObjectId = (__ \ '_id).json.copyFrom(getObjectId)

  /** Generates a new ID and adds it to your JSON using Json extended notation for BSON */
  val generateId = (__ \ '_id \ '$oid).json.put(JsString(BSONObjectID.generate.stringify))

  /** Updates Json by adding both ID and date */
  val addMongoId: Reads[JsObject] = __.json.update(generateId)

  def toMongoUpdate: Reads[JsObject] = (__ \ '$set).json.copyFrom(__.json.pickBranch)
}
