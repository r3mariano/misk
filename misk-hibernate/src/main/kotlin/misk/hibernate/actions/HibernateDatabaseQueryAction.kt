package misk.hibernate.actions

import com.google.inject.Injector
import misk.MiskCaller
import misk.exceptions.BadRequestException
import misk.exceptions.UnauthorizedException
import misk.hibernate.DbEntity
import misk.hibernate.HibernateEntity
import misk.hibernate.Operator
import misk.hibernate.Query
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
import java.lang.reflect.ParameterizedType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/** Runs query from Database Query dashboard tab against DB and returns results */
@Singleton
class HibernateDatabaseQueryAction @Inject constructor(
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

  @Post(HIBERNATE_QUERY_WEBACTION_PATH)
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AdminDashboardAccess
  fun getAll(@RequestBody request: Request): Response {
    val caller = callerProvider.get()!!
    val queryClass = request.queryClass
    val metadata =
        databaseQueryMetadata.find { it.queryClass == queryClass } ?: throw BadRequestException(
            "Invalid Query Class")

    val isDynamicQuery = queryClass.endsWith("DynamicQuery")

    // Find the transacter for the query class

    val transacterBindings = injector.findBindingsByType(Transacter::class.typeLiteral())
    val transacter = transacterBindings.find { transacterBinding ->
      transacterBinding.provider.get().entities().map { it.simpleName!! }
          .contains(metadata.entityClass)
    }?.provider?.get() ?: throw BadRequestException(
        "[dbEntity=${metadata.entityClass}] has no associated Transacter")

    val results = if (caller.isAllowed(metadata.allowedCapabilities, metadata.allowedServices)) {
      transacter.transaction { session ->
        val (dbEntity: KClass<out DbEntity<*>>, configuredQuery: Query<out DbEntity<*>>) = findDbEntityAndQuery(
            entities = transacter.entities(),
            isDynamicQuery = isDynamicQuery,
            request = request,
            metadata = metadata
        )

        configuredQuery.apply {
          request.query.forEach { (key, value) ->
            val queryMethodType = key.split("/").first()
            val queryMethodFunctionName = key.split("/").last()
//            val function = this::class.functions.find { it.name == queryMethodFunctionName }
//                ?: throw BadRequestException(
//                    "No function on [query=${metadata.queryClass}] with [name=${queryMethodFunctionName}]")
//
//            when {
//              queryMethodType == "Select" -> {
//                selectFunction = function
//              }
//              function.parameters.isEmpty() -> {
//                function.call()
//              }
//              function.parameters.size == 1 -> {
//                function.call(this)
//              }
//              function.parameters.size == 2 && (function.valueParameters.firstOrNull()?.type?.classifier as KClass<*>?) == String::class -> {
//                // TODO(adrw) handle non-string types
//                function.call(this, value.toString())
//              }
//              else -> {
//                function.call(this, value)
//              }
//            }
            when (queryMethodType) {
              "Constraint" -> {
                metadata.constraints.find { it.parametersTypeName == key }?.let {
                  dynamicAddConstraint(it.path, Operator.valueOf(it.operator),
                      (value as Map<String, String>)[it.name])
                }
              }
              "Order" -> {
                metadata.orders.find { it.parametersTypeName == key }?.let {
                  dynamicAddOrder(it.path, it.ascending)
                }
              }
            }
          }
        }

        // Find Select Function
        var selectMetadata: DatabaseQueryMetadata.SelectMetadata? = null
        var selectPaths: List<String> = listOf()
        val requestSelect =
            request.query.entries.find { (key, _) -> key.split("/").first() == "Select" }
        if (requestSelect != null) {
          selectMetadata = metadata.selects.find { it.name == requestSelect.key.split("/").last() }
          // Handle Dynamic...etc
          selectPaths = (request.query.entries.find { (key, _) ->
            key.split("/").first() == "Select"
          }?.value as Map<String, List<String>>)["Paths"]!!
        }

        if (selectMetadata == null) {
          // Use default select function
          dbEntity.members.map { it.name } to configuredQuery.list(session)
        } else {
          selectPaths to configuredQuery.dynamicList(session, selectPaths)
//          selectFunction!!.call(configuredQuery, session)
        }
      }
    } else {
      throw UnauthorizedException("Unauthorized to query [dbEntity=${metadata.entityClass}]")
    }

    return Response(headers = results.first, rows = results.second)
  }

  private fun findDbEntityAndQuery(
    entities: Set<KClass<out DbEntity<*>>>,
    isDynamicQuery: Boolean,
    request: Request,
    metadata: DatabaseQueryMetadata,
  ): Pair<KClass<out DbEntity<*>>, Query<out DbEntity<*>>> {
    lateinit var dbEntity: KClass<out DbEntity<*>>
    lateinit var configuredQuery: Query<out DbEntity<*>>
    if (isDynamicQuery) {
      dbEntity = entities.find { it.simpleName == request.entityClass }
          ?: throw BadRequestException(
              "[dbEntity=${metadata.entityClass}] is not an installed HibernateEntity")
      configuredQuery = queryFactory.dynamicQuery(dbEntity)
    } else {
      val query = queries.map { it.query }.find { it.simpleName == metadata.queryClass }
          ?: throw BadRequestException("[query=${metadata.queryClass}] does not exist")
      dbEntity = ((query.typeLiteral().getSupertype(
          Query::class.java).type as ParameterizedType).actualTypeArguments.first() as Class<DbEntity<*>>).kotlin
      configuredQuery = queryFactory.newQuery(query)
    }
    return Pair(dbEntity, configuredQuery)
  }

  data class Request(
    val entityClass: String,
    val queryClass: String,
    /** Query request takes form of query field name (ie. MovieQuery.releaseDateAsc) to value (true) */
    val query: Map<String, Any>
  )

  data class Response(
    /** In testing, just return string instead of nicely formatted table */
//    val results: String,
    val headers: List<String>,
    val rows: List<Any>
  )

  companion object {
    const val HIBERNATE_QUERY_WEBACTION_PATH = "/api/database/query/hibernate"
  }
}
