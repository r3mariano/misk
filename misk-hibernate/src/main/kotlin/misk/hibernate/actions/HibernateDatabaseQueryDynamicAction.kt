package misk.hibernate.actions

import com.google.inject.Injector
import misk.MiskCaller
import misk.exceptions.BadRequestException
import misk.exceptions.UnauthorizedException
import misk.hibernate.DbEntity
import misk.hibernate.ReflectionQuery
import misk.hibernate.Transacter
import misk.inject.typeLiteral
import misk.scope.ActionScoped
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes
import misk.web.metadata.DatabaseQueryMetadata
import misk.web.metadata.DatabaseQueryMetadata.Companion.DYNAMIC_QUERY_KCLASS_SUFFIX
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.staticProperties

/** Runs query from Database Query dashboard tab against DB and returns results */
@Singleton
class HibernateDatabaseQueryDynamicAction @Inject constructor(
  @JvmSuppressWildcards private val callerProvider: ActionScoped<MiskCaller?>,
  val databaseQueryMetadata: List<DatabaseQueryMetadata>,
  val queries: List<HibernateQuery>,
  val injector: Injector,
) : WebAction {

  val maxMaxRows = 500
  val rowCountErrorLimit = 30
  val rowCountWarningLimit = 20
  private val queryFactory = ReflectionQuery.Factory(ReflectionQuery.QueryLimitsConfig(
      maxMaxRows, rowCountErrorLimit, rowCountWarningLimit))

  @Post(HIBERNATE_QUERY_DYNAMIC_WEBACTION_PATH)
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AdminDashboardAccess
  fun getAll(@RequestBody request: Request): Response {
    val caller = callerProvider.get()!!
    val queryClass = request.queryClass
    val metadata =
        databaseQueryMetadata.find { it.queryClass == queryClass } ?: throw BadRequestException(
            "Invalid Query Class")

    val isDynamicQuery = queryClass.endsWith(DYNAMIC_QUERY_KCLASS_SUFFIX)
    if (!isDynamicQuery) {
      throw BadRequestException(
          "[queryClass=$queryClass] is not a DynamicQuery and can't be handled by HibernateDatabaseQueryDynamicAction")
    }

    val transacterBindings = injector.findBindingsByType(Transacter::class.typeLiteral())
    val transacter = transacterBindings.find { transacterBinding ->
      transacterBinding.provider.get().entities().map { it.simpleName!! }
          .contains(metadata.entityClass)
    }?.provider?.get() ?: throw BadRequestException(
        "[dbEntity=${metadata.entityClass}] has no associated Transacter")

    val results = if (caller.isAllowed(metadata.allowedCapabilities, metadata.allowedServices)) {
      transacter.transaction { session ->
        val dbEntity = transacter.entities().find { it.simpleName == request.entityClass }
                ?: throw BadRequestException(
                    "[dbEntity=${metadata.entityClass}] is not an installed HibernateEntity"
                )
        val selectPaths = request.query.select?.paths ?: dbEntity.memberProperties.map { it.name }
        val configuredQuery = queryFactory.dynamicQuery(dbEntity).apply {
          request.query.constraints?.forEach { (path, operator, value) ->
            if (path == null) throw BadRequestException("Constraint path must be non-null")
            if (operator == null) throw BadRequestException("Constraint operator must be non-null")
            dynamicAddConstraint(path = path, operator = operator, value = value)
          }
          request.query.orders?.forEach { (path, ascending) ->
            check(path != null) { "Order path must be non-null" }
            check(ascending != null) { "Order ascending must be non-null" }
            dynamicAddOrder(path = path, asc = ascending)
          }
        }

        val rows = configuredQuery.dynamicList(session, selectPaths)
        selectPaths to rows
      }
    } else {
      throw UnauthorizedException("Unauthorized to query [dbEntity=${metadata.entityClass}]")
    }

    return Response(headers = results.first, rows = results.second)
  }

  data class Request(
    val entityClass: String,
    val queryClass: String,
    val query: HibernateDatabaseQueryMetadataFactory.Companion.DynamicQuery
  )

  data class Response(
    /** In testing, just return string instead of nicely formatted table */
//    val results: String,
    val headers: List<String>,
    val rows: List<Any>
  )

  companion object {
    const val HIBERNATE_QUERY_DYNAMIC_WEBACTION_PATH = "/api/database/query/hibernate/dynamic"
  }
}
