package infra.mongo

import infra.wished.JsonApiError
import reactivemongo.core.commands.LastError

trait MongoError {
  def serviceName: String

  def emptyId() = JsonApiError(500, "empty_id", "Empty ID", serviceName)
  def databaseError(le: LastError) = JsonApiError(500, "db_error", "DB Error", serviceName)
  def notFound() = JsonApiError(404, "not_found", "Item Not Found", serviceName)
}