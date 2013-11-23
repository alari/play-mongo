package mirari.mongo

import play.api.Play.current

import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global
import play.api.libs.json._

import play.modules.reactivemongo.ReactiveMongoPlugin
import play.modules.reactivemongo.json.collection.JSONCollection

import reactivemongo.core.commands.LastError
import reactivemongo.bson.BSONObjectID
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.QueryOpts


/**
 * @author alari
 * @since 7/4/13 11:07 PM
 */
abstract class MongoDAO[D <% MongoDomain[_]](val collectionName: String) extends MongoImplicits {
  /**
   * ReactiveMongo database accessor
   * @return
   */
  protected def db = ReactiveMongoPlugin.db

  /**
   * You must implement it like Json.format[D]
   * @return
   */
  protected def format: Format[D]

  /**
   * Internal implicit format
   * @return
   */
  implicit protected def f = format

  /**
   * Converts string id into database representation
   * @param id string
   * @return $oid or string representation
   */
  protected def toId(id: String): JsValue

  /**
   * _id -> (id)
   * @param id string
   * @return
   */
  protected def toObjectId(id: String) = Json.obj("_id" -> toId(id))

  /**
   * Reactive's JSON collection
   * @return
   */
  protected def collection: JSONCollection = db.collection[JSONCollection](collectionName)

  protected val Ascending = IndexType.Ascending
  protected val Descending = IndexType.Descending

  /**
   * Ensures common non-unique index
   * @param key column -> index type
   */
  protected def ensureIndex(key: (String, IndexType)*) {
    ensureIndex(unique = false)(key: _*)
  }

  /**
   * Ensures the new index on fields
   * @param unique unique
   * @param dropDups dropDups
   * @param sparse sparse -- means whether to create an index when not all fields are provided or not
   * @param name Index name
   * @param key column -> Ascending, Descending or other index type
   * @return
   */
  protected def ensureIndex(unique: Boolean = false, dropDups: Boolean = false, sparse: Boolean = false, name: Option[String] = None)(key: (String, IndexType)*) {
    collection.indexesManager.ensure(Index(key, unique = unique, dropDups = dropDups, sparse = sparse, name = name))
  }

  /**
   * Returns an object by id if it's provided,
   * None if it's not,
   * throws NotFound if it's provided, but no object is found
   * @param id optional id
   * @return
   */
  def getById(id: Option[String]): Future[Option[D]] = id match {
    case Some(_id) =>
      getById(_id).map(Some(_))
    case _ =>
      Future(None)
  }

  /**
   * Returns an object by id or throws NotFound
   * @param id
   * @return
   */
  def getById(id: String): Future[D] =
    collection.find(toObjectId(id)).one[D] map {
      case Some(d) => d
      case None => throw NotFound()
    }

  /**
   * Returns list of objects by ids. Output may be less then input
   * @param ids ids
   * @return
   */
  def getByIds(ids: Seq[String]): Future[List[D]] =
    collection.find(
      Json.obj("_id" ->
        Json.obj("$in" ->
          ids.map(toId)
        )))
      .cursor[D]
      .collect[List](ids.length)

  /**
   * Updates an object by id
   * @param obj updated object
   * @return updated object
   */
  def update(obj: D): Future[D] = {
    if (!obj.hasId) throw EmptyId()
    (__ \ "_id").prune(Json.toJson(obj)).asOpt.map {
      json =>
        collection.update(toObjectId(obj.id), Json.obj("$set" -> json)).map(failOrObj(obj))
    } getOrElse Future.failed(NotFound())
  }

  /**
   * Sets properties for id
   * @param id object id to chenge
   * @param obj properties to set
   * @return changed object
   */
  def set(id: String, obj: JsObject): Future[D] =
    collection.update(toObjectId(id), Json.obj("$set" -> Json.toJson(obj))).flatMap {
      lastError =>
        lastError.inError match {
          case true =>
            Future.failed(DatabaseError(lastError))
          case false =>
            getById(id)
        }
    }

  /**
   * Tries to find an object
   * @param finder query
   * @return
   */
  def findOne(finder: JsObject): Future[Option[D]] =
    collection.find(finder).one[D]

  /**
   * Finder helper
   * @param finder query document
   * @return sequence
   */
  def find(finder: JsObject): Future[List[D]] = collection
    .find(finder)
    .cursor[D]
    .collect[List]()

  /**
   * Finder helper
   * @param finder query
   * @param offset skipN
   * @param limit upTo
   * @return sequence
   */
  def find(finder: JsObject, offset: Int, limit: Int): Future[List[D]] = collection
    .find(finder)
    .options(QueryOpts(skipN = offset))
    .cursor[D]
    .collect[List](limit)

  /**
   * Finder helper
   * @param finder query
   * @param sort sorting document
   * @return sequence
   */
  def find(finder: JsObject, sort: JsObject): Future[List[D]] = collection
    .find(finder)
    .sort(sort)
    .cursor[D]
    .collect[List]()

  /**
   * Finder helper
   * @param finder query
   * @param sort sorting document
   * @param offset skipN
   * @param limit upTo
   * @return sequence
   */
  def find(finder: JsObject, sort: JsObject, offset: Int, limit: Int): Future[List[D]] = collection
    .find(finder)
    .options(QueryOpts(skipN = offset))
    .sort(sort)
    .cursor[D]
    .collect[List](limit)

  /**
   * Removes object by id
   * @param id to remove
   * @return true
   */
  def remove(id: String): Future[Boolean] =
    collection.remove(toObjectId(id)).map(failOrTrue)


  /**
   * Inserts new object into collection. Note: _id must be set
   * @param obj
   * @return
   */
  protected def insert(obj: D): Future[D] = {
    if (!obj.hasId) throw EmptyId()
    collection.insert(obj).map(failOrObj(obj))
  }

  /**
   * Returns passed object if not in error state, throws DatabaseError otherwise
   * @param obj to return
   * @param err current error
   * @return obj
   */
  protected def failOrObj(obj: D)(err: LastError): D =
    if (err.inError) throw DatabaseError(err) else obj

  /**
   * Throws DatabaseError in error state, returns true otherwise
   * @param err last error
   * @return true
   */
  protected def failOrTrue(err: LastError): Boolean =
    if (err.inError) throw DatabaseError(err) else true
}

object MongoDAO {

  abstract class Oid[D <: MongoDomain.Oid](collectionName: String) extends MongoDAO[D](collectionName) with MongoImplicits {
    protected def generateSomeId = Some(BSONObjectID.generate)

    override protected def toId(id: String) = Json.obj("$oid" -> id)

    /**
     * Returns list of objects by ids. Output may be less then input
     * @param ids ids
     * @return
     */
    override def getByIds(ids: Seq[String]): Future[List[D]] =
      collection.find(
        Json.obj("_id" ->
          Json.obj("$in" ->
            ids
              .view
              .map(BSONObjectID.parse)
              .map{i => play.api.Logger.debug(i.toString);i}
              .filter(_.isSuccess)
              .map(_.get)
              .force
          )))
        .cursor[D]
        .collect[List](ids.length)
  }

  abstract class Str[D <: MongoDomain.Str](collectionName: String) extends MongoDAO[D](collectionName) {
    override protected def toId(id: String) = JsString(id)
  }

}