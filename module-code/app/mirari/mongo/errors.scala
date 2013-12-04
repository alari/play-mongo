package mirari.mongo

import reactivemongo.core.commands.LastError
import mirari.wished.Unwished

abstract sealed class MongoError(status: Int) extends Unwished(status, None)

case class DatabaseError(lastError: LastError) extends MongoError(500)

case class EmptyId() extends MongoError(404)

case class NotFound(what: String) extends MongoError(404)