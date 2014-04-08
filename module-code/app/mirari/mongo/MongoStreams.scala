package mirari.mongo

import scala.concurrent.{Future, ExecutionContext}
import play.api.libs.json.{Json, JsObject}
import reactivemongo.api.QueryOpts
import play.api.libs.iteratee.{Enumerator, Enumeratee}

/**
 * @author alari
 * @since 4/8/14
 */
trait MongoStreams[D <: MongoDomain[_]] {
  self: MongoDAO[D] =>

  object Stream {
    private implicit def toEnum[K](f: => Future[Option[K]])(implicit ec: ExecutionContext) = Enumerator.generateM(f)

    private implicit def toEnumO[K](f: => Future[K])(implicit ec: ExecutionContext) = Enumerator.generateM(f.map(Option.apply))

    /**
     * Maps ids to objects
     * @param ec
     * @return
     */
    def getById(implicit ec: ExecutionContext): Enumeratee[String, D] =
      Enumeratee.mapFlatten(id =>
        collection.find(toObjectId(id)).one[D]
      )

    /**
     * Enumerates ids to objects
     * @param ec
     * @return
     */
    def getByIds(implicit ec: ExecutionContext): Enumeratee[TraversableOnce[String], D] =
      Enumeratee.mapFlatten(ids => collection.find(byIdsFinder(ids)).cursor[D].enumerate())

    /**
     * Updates (replaces) object, returns an updated one
     * @param ec
     * @return
     */
    def update(implicit ec: ExecutionContext): Enumeratee[D, D] =
      Enumeratee.mapFlatten(obj => self.update(obj))

    /**
     * Sets a field by a finder
     * @param ec
     * @return
     */
    def setMulti(implicit ec: ExecutionContext): Enumeratee[(JsObject, JsObject), Boolean] =
    Enumeratee.mapFlatten(finder => collection.update(finder._1, Json.obj("$set" -> finder._2), multi = true).map(failOrTrue))

    /**
     * Updates some fields with $set modifier by id
     * @param ec
     * @return
     */
    def set(implicit ec: ExecutionContext): Enumeratee[(String, JsObject), D] =
      Enumeratee.mapFlatten(obj => self.set(obj._1, obj._2))

    /**
     * Finds one object by finder
     * @param ec
     * @return
     */
    def findOne(implicit ec: ExecutionContext): Enumeratee[JsObject, D] =
      Enumeratee.mapFlatten(finder => collection.find(finder).one[D])

    /**
     * Finds all objects by finder
     * @param ec
     * @return
     */
    def findBy(implicit ec: ExecutionContext): Enumeratee[JsObject, D] =
      Enumeratee.mapFlatten(finder => collection.find(finder).cursor[D].enumerate())

    /**
     * Enumerates all objects in a collection
     * @param ec
     * @return
     */
    def findAll(implicit ec: ExecutionContext): Enumerator[D] =
      collection.find(Json.obj()).cursor[D].enumerate()

    /**
     * Enumerates all objects in a collection, sorted
     * @param ec
     * @return
     */
    def findAllSorted(implicit ec: ExecutionContext): Enumeratee[JsObject,D] =
    Enumeratee.mapFlatten(sort => collection.find(Json.obj()).sort(sort).cursor[D].enumerate())

    /**
     * Enumerates by finder, offset, limit
     * @param ec
     * @return
     */
    def findOffsetLimit(implicit ec: ExecutionContext): Enumeratee[(JsObject, Int, Int), D] =
      Enumeratee.mapFlatten(finder => collection
        .find(finder._1)
        .options(QueryOpts(skipN = finder._2))
        .cursor[D]
        .enumerate(maxDocs = finder._3)
      )

    /**
     * Enumerates by finder and sorting
     * @param ec
     * @return
     */
    def findSorted(implicit ec: ExecutionContext): Enumeratee[(JsObject, JsObject), D] =
      Enumeratee.mapFlatten(finder => collection
        .find(finder._1)
        .sort(finder._2)
        .cursor[D]
        .enumerate())

    /**
     * Finds objects by finder, sorting, offset, limit
     * @param ec
     * @return
     */
    def find(implicit ec: ExecutionContext): Enumeratee[(JsObject, JsObject, Int, Int), D] =
      Enumeratee.mapFlatten(finder => collection
        .find(finder._1)
        .sort(finder._2)
        .options(QueryOpts(skipN = finder._3))
        .cursor[D]
        .enumerate(maxDocs = finder._4))

    /**
     * Removes objects by id
     * @param ec
     * @return
     */
    def removeById(implicit ec: ExecutionContext): Enumeratee[String, Boolean] =
      Enumeratee.mapFlatten(id => self.remove(id))

    /**
     * Removes one object by finder
     * @param ec
     * @return
     */
    def removeOne(implicit ec: ExecutionContext): Enumeratee[JsObject, Boolean] =
      Enumeratee.mapFlatten(id => self.remove(id))

    /**
     * Removes all objects by finder
     * @param ec
     * @return
     */
    def removeAll(implicit ec: ExecutionContext): Enumeratee[JsObject, Boolean] =
      Enumeratee.mapFlatten(id => self.removeAll(id))

    /**
     * Inserts a new object
     * @param ec
     * @return
     */
    def insert(implicit ec: ExecutionContext): Enumeratee[D, D] =
      Enumeratee.mapFlatten(obj => self.insert(obj))

  }

}
