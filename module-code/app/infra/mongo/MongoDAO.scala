package infra.mongo

import play.api.Play.current

import scala.concurrent.{ExecutionContext, Future}
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
abstract class MongoDAO[D <: MongoDomain[_]](val collectionName: String) extends MongoImplicits {

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
    import play.api.libs.concurrent.Execution.Implicits.defaultContext
    collection.indexesManager.ensure(Index(key, unique = unique, dropDups = dropDups, sparse = sparse, name = name))
  }

  protected def byIdsFinder(ids: TraversableOnce[String]) =
    Json.obj("_id" ->
      Json.obj("$in" ->
        ids.map(toId).toSeq
      ))

  /**
   * Returns an object by id if it's provided,
   * None if it's not,
   * throws NotFound if it's provided, but no object is found
   * @param id optional id
   * @return
   */
  def getById(id: Option[String])(implicit ec: ExecutionContext): Future[Option[D]] = id match {
    case Some(_id) =>
      getById(_id).map(Some(_))
    case _ =>
      Future(None)
  }

  /**
   * Returns an object by id or throws NotFound
   * @param id id
   * @return
   */
  def getById(id: String)(implicit ec: ExecutionContext): Future[D] =
    try {
      collection.find(toObjectId(id)).one[D] map {
        case Some(d) => d
        case None => throw NotFound(s"$id in $collectionName")
      }
    } catch {
      case e: Throwable =>
        Future.failed(e)
    }

  /**
   * Finds one object by id
   * @param id
   * @param ec
   * @return
   */
  def findById(id: String)(implicit ec: ExecutionContext): Future[Option[D]] =
    findOne(toObjectId(id))

  /**
   * Returns list of objects by ids. Output may be less then input
   * @param ids ids
   * @return
   */
  def getByIds(ids: Seq[String])(implicit ec: ExecutionContext): Future[List[D]] =
    try {
      collection.find(
        byIdsFinder(ids))
        .cursor[D]
        .collect[List](ids.length)
    } catch {
      case e: Throwable =>
        Future.failed(e)
    }

  /**
   * Updates an object by id
   * @param obj updated object
   * @return updated object
   */
  def update(obj: D)(implicit ec: ExecutionContext): Future[D] =
    try {
      if (!obj.hasId) Future.failed(EmptyId())
      else
        (__ \ "_id").prune(Json.toJson(obj)).asOpt.map {
          json =>
            collection.update(toObjectId(obj.id), Json.obj("$set" -> json)).map(failOrObj(obj))
        } getOrElse Future.failed(NotFound(s"${obj.id} in $collectionName during update"))
    } catch {
      case e: Throwable =>
        Future.failed(e)
    }

  /**
   * Replaces an object by id
   * @param obj updated object
   * @return updated object
   */
  def replace(obj: D)(implicit ec: ExecutionContext): Future[D] =
    try {
      if (!obj.hasId) Future.failed(EmptyId())
      else
        (__ \ "_id").prune(Json.toJson(obj)).asOpt.map {
          json =>
            collection.update(toObjectId(obj.id), json).map(failOrObj(obj))
        } getOrElse Future.failed(NotFound(s"${obj.id} in $collectionName during replace"))
    } catch {
      case e: Throwable =>
        Future.failed(e)
    }


  /**
   * Sets properties for id
   * @param id object id to chenge
   * @param obj properties to set
   * @return changed object
   */
  def set(id: String, obj: JsObject)(implicit ec: ExecutionContext): Future[D] =
    try {
      collection.update(toObjectId(id), Json.obj("$set" -> Json.toJson(obj))).flatMap {
        lastError =>
          lastError.inError match {
            case true =>
              Future.failed(DatabaseError(lastError))
            case false =>
              getById(id)
          }
      }
    } catch {
      case e: Throwable =>
        Future.failed(e)
    }

  /**
   * Tries to find an object
   * @param finder query
   * @return
   */
  def findOne(finder: JsObject)(implicit ec: ExecutionContext): Future[Option[D]] =
    try {
      collection.find(finder).one[D]
    } catch {
      case e: Throwable =>
        Future.failed(e)
    }

  /**
   * Finder helper
   * @param finder query document
   * @return sequence
   */
  def find(finder: JsObject)(implicit ec: ExecutionContext): Future[List[D]] =
    try {
      collection
        .find(finder)
        .cursor[D]
        .collect[List]()
    } catch {
      case e: Throwable =>
        Future.failed(e)
    }

  /**
   * Finder helper
   * @param finder query
   * @param offset skipN
   * @param limit upTo
   * @return sequence
   */
  def find(finder: JsObject, offset: Int, limit: Int)(implicit ec: ExecutionContext): Future[List[D]] =
    try {
      collection
        .find(finder)
        .options(QueryOpts(skipN = offset))
        .cursor[D]
        .collect[List](limit)
    } catch {
      case e: Throwable =>
        Future.failed(e)
    }

  /**
   * Finder helper
   * @param finder query
   * @param sort sorting document
   * @return sequence
   */
  def find(finder: JsObject, sort: JsObject)(implicit ec: ExecutionContext): Future[List[D]] =
    try {
      collection
        .find(finder)
        .sort(sort)
        .cursor[D]
        .collect[List]()
    } catch {
      case e: Throwable =>
        Future.failed(e)
    }

  /**
   * Finder helper
   * @param finder query
   * @param sort sorting document
   * @param offset skipN
   * @param limit upTo
   * @return sequence
   */
  def find(finder: JsObject, sort: JsObject, offset: Int, limit: Int)(implicit ec: ExecutionContext): Future[List[D]] =
    try {
      collection
        .find(finder)
        .options(QueryOpts(skipN = offset))
        .sort(sort)
        .cursor[D]
        .collect[List](limit)
    } catch {
      case e: Throwable =>
        Future.failed(e)
    }

  /**
   * Removes object by id
   * @param id to remove
   * @return true
   */
  def remove(id: String)(implicit ec: ExecutionContext): Future[Boolean] =
    try {
      collection.remove(toObjectId(id)).map(failOrTrue)
    } catch {
      case e: Throwable =>
        Future.failed(e)
    }

  /**
   * Removes single object by finder
   * @param finder
   * @param ec
   * @return
   */
  def remove(finder: JsObject)(implicit ec: ExecutionContext): Future[Boolean] =
    try {
      collection.remove(finder, firstMatchOnly = true).map(failOrTrue)
    } catch {
      case e: Throwable =>
        Future.failed(e)
    }

  /**
   * Removes all objects by finder
   * @param finder
   * @param ec
   * @return
   */
  def removeAll(finder: JsObject)(implicit ec: ExecutionContext): Future[Boolean] =
    try {
      collection.remove(finder, firstMatchOnly = false).map(failOrTrue)
    } catch {
      case e: Throwable =>
        Future.failed(e)
    }

  /**
   * Inserts new object into collection. Note: _id must be set
   * @param obj object to insert
   * @return
   */
  protected def insert(obj: D)(implicit ec: ExecutionContext): Future[D] =
    if (!obj.hasId)
      Future.failed(EmptyId())
    else try {
      collection.insert(obj).map(failOrObj(obj))
    } catch {
      case e: Throwable =>
        Future.failed(e)
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

    override protected def toId(id: String): JsValue = Json.toJson(BSONObjectID.parse(id).getOrElse(throw new EmptyId()))

    override protected def byIdsFinder(ids: TraversableOnce[String]) =
      Json.obj("_id" ->
        Json.obj("$in" ->
          ids
            .map(BSONObjectID.parse)
            .filter(_.isSuccess)
            .map(_.get)
            .toSeq
        ))
  }

  abstract class Str[D <: MongoDomain.Str](collectionName: String) extends MongoDAO[D](collectionName) {
    override protected def toId(id: String) = JsString(id)
  }

}