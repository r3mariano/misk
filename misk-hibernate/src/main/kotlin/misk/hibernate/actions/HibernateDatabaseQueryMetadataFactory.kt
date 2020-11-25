package misk.hibernate.actions

import misk.hibernate.Constraint
import misk.hibernate.DbEntity
import misk.hibernate.Id
import misk.hibernate.Operator
import misk.hibernate.Order
import misk.hibernate.Projection
import misk.hibernate.Property
import misk.hibernate.Query
import misk.hibernate.Select
import misk.hibernate.Session
import misk.hibernate.actions.HibernateDatabaseQueryAction.Companion.HIBERNATE_QUERY_WEBACTION_PATH
import misk.security.authz.AccessAnnotationEntry
import misk.web.RequestTypes.Companion.createEnumField
import misk.web.RequestTypes.Companion.maybeCreatePrimitiveField
import misk.web.metadata.DatabaseQueryMetadata
import misk.web.metadata.Field
import misk.web.metadata.Type
import javax.inject.Inject
import javax.inject.Singleton
import javax.persistence.Table
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation

@Singleton
class HibernateDatabaseQueryMetadataFactory @Inject constructor(
  val accessAnnotationEntries: List<AccessAnnotationEntry>
) {
  private val DYNAMIC_CONSTRAINT_TYPE_NAME = "Constraint/Dynamic"
  private val DYNAMIC_ORDER_TYPE_NAME = "Order/Dynamic"
  private val DYNAMIC_SELECT_TYPE_NAME = "Select/Dynamic"
  private val DYNAMIC_ROOT_TYPE = "DynamicQuery"
  private val DYNAMIC_TYPES = listOf(
      DYNAMIC_CONSTRAINT_TYPE_NAME to Type(fields = listOf(
          Field(name = "Path", repeated = false, type = "String"),
          createEnumField(Operator::class, "Operator", false),
          Field(name = "Value", repeated = false, type = "String"),
      )),
      DYNAMIC_ORDER_TYPE_NAME to Type(fields = listOf(
          Field(name = "Path", repeated = false, type = "String"),
          Field(name = "Ascending", repeated = false, type = "Boolean"),
      )),
      "Order/DynamicList" to Type(fields = listOf(
          Field(name = "Dynamic Orders", repeated = true, type = DYNAMIC_ORDER_TYPE_NAME)
      )),
      DYNAMIC_SELECT_TYPE_NAME to Type(fields = listOf(
          Field(name = "Paths", repeated = true, type = "String")
      )),
      DYNAMIC_ROOT_TYPE to Type(fields = listOf(
          Field(name = "Constraints", repeated = true,
              type = DYNAMIC_CONSTRAINT_TYPE_NAME),
          Field(name = "Orders", repeated = true, type = DYNAMIC_ORDER_TYPE_NAME),
          Field(name = "Select", repeated = false, type = DYNAMIC_SELECT_TYPE_NAME),
      )),
  )

  fun <T : DbEntity<T>> fromQuery(
    dbEntityClass: KClass<T>,
    queryClass: KClass<out Query<T>>? = null,
    accessAnnotationClass: KClass<out Annotation>? = null,
  ): DatabaseQueryMetadata {
    val table = dbEntityClass.findAnnotation<Table>()!!

    val accessAnnotationEntry =
        accessAnnotationEntries.find { it.annotation == accessAnnotationClass }
    val allowedCapabilities = accessAnnotationEntry?.capabilities?.toSet() ?: setOf()
    val allowedServices = accessAnnotationEntry?.services?.toSet() ?: setOf()

    val constraintsResult = mutableListOf<Pair<Type, DatabaseQueryMetadata.ConstraintMetadata>>()
    val ordersResult = mutableListOf<Pair<Type, DatabaseQueryMetadata.OrderMetadata>>()
    val selectsResult = mutableListOf<Pair<Type, DatabaseQueryMetadata.SelectMetadata>>()

    val queryTypesDynamicToInclude = if (queryClass != null) {
      addStaticQueryMethodMetadata(queryClass, constraintsResult, ordersResult, selectsResult)
      listOf()
    } else {
      DYNAMIC_TYPES
    }

    val queryTypes = queryTypesDynamicToInclude +
        constraintsResult.map { it.second.parametersTypeName to it.first } +
        ordersResult.map { it.second.parametersTypeName to it.first } +
        selectsResult.map { it.second.parametersTypeName to it.first }

    val queryType = if (constraintsResult.isEmpty() && ordersResult.isEmpty() && selectsResult.isEmpty()) {
          DYNAMIC_TYPES.toMap()[DYNAMIC_ROOT_TYPE]!!
        } else {
          Type(fields = queryTypes.map { (name, _) ->
            Field(name = name, repeated = false, type = name)
          })
        }

    return DatabaseQueryMetadata(
        queryWebActionPath = HIBERNATE_QUERY_WEBACTION_PATH,
        allowedCapabilities = allowedCapabilities,
        allowedServices = allowedServices,
        accessAnnotation = accessAnnotationClass,
        table = table.name,
        entityClass = dbEntityClass,
        queryClass = queryClass,
        constraints = constraintsResult.map { it.second },
        orders = ordersResult.map { it.second },
        selects = selectsResult.map { it.second },
        types = queryTypes.toMap() + mapOf("queryType" to queryType)
    )
  }

  private fun <T : DbEntity<T>> addStaticQueryMethodMetadata(
    queryClass: KClass<out Query<T>>?,
    constraintsResult: MutableList<Pair<Type, DatabaseQueryMetadata.ConstraintMetadata>>,
    ordersResult: MutableList<Pair<Type, DatabaseQueryMetadata.OrderMetadata>>,
    selectsResult: MutableList<Pair<Type, DatabaseQueryMetadata.SelectMetadata>>
  ) {
    if (queryClass != null) {
      for (function in queryClass.criteriaFunctions()) {
        val constraint = function.findAnnotation<Constraint>()
        val order = function.findAnnotation<Order>()
        val select = function.findAnnotation<Select>()

        when {
          constraint != null -> constraintsResult += constraintMetadata(queryClass, function,
              constraint)
          order != null -> ordersResult += orderMetadata(queryClass, function, order)
          select != null -> selectsResult += selectMetadata(queryClass, function, select,
              function.returnType)
        }
      }
    }
  }

//  private fun <T : DbEntity<T>> addDynamicQueryMethodMetadata(
//    queryClass: KClass<out Query<T>>,
//    constraintsResult: MutableList<Pair<Type, DatabaseQueryMetadata.ConstraintMetadata>>,
//    ordersResult: MutableList<Pair<Type, DatabaseQueryMetadata.OrderMetadata>>,
//    selectsResult: MutableList<Pair<Type, DatabaseQueryMetadata.SelectMetadata>>
//  ) {
//    val dynamicConstraints = DatabaseQueryMetadata.ConstraintMetadata(
//        name = "DynamicSelect",
//        path = "",
//        parametersTypeName = "Select/${queryClass.simpleName}/DynamicSelect",
//        operator = ""
//    )
//    val dynamicConstraintsType = Type(fields = listOf(
//        Field(name = "Constraint/DynamicList", repeated = true,
//            type = DYNAMIC_CONSTRAINT_TYPE_NAME)
//    ))
//    constraintsResult += Pair(dynamicConstraintsType, dynamicConstraints)
//
//    val dynamicOrders = DatabaseQueryMetadata.OrderMetadata(
//        name = "DynamicSelect",
//        path = "",
//        ascending = false,
//        parametersTypeName = "Select/${queryClass.simpleName}/DynamicOrder"
//    )
//    val dynamicOrdersType = Type(fields = listOf(
//        Field(name = "Order/DynamicList", repeated = true, type = DYNAMIC_ORDER_TYPE_NAME)
//    ))
//    ordersResult += Pair(dynamicOrdersType, dynamicOrders)
//  }

  private fun constraintMetadata(
    queryClass: KClass<out Query<*>>,
    function: KFunction<*>,
    constraint: Constraint
  ): Pair<Type, DatabaseQueryMetadata.ConstraintMetadata> {
    // Size - 1 since `this` is the first parameter.
    check(function.parameters.size - 1 <= 1)
    val onlyParameter = function.parameters.firstOrNull { it.name != null }

    val field = onlyParameter?.name?.let { name ->
      createField(onlyParameter.type, name, onlyParameter.isVararg)
    } ?: Field(name = "Add Constraint", type = Boolean::class.simpleName!!, repeated = false)
    return Type(
        fields = listOf(field)
    ) to DatabaseQueryMetadata.ConstraintMetadata(
        name = function.name,
        parametersTypeName = "Constraint/${queryClass.simpleName}/${function.name}",
        path = constraint.path,
        operator = constraint.operator.name
    )
  }

  //  private fun constraintPair(function: KFunction<*>, constraint: Constraint): Pair<String, Field?> {
//    // Size - 1 since `this` is the first parameter.
//    check(function.parameters.size - 1 <= 1)
//    val onlyParameter =
//        function.parameters.firstOrNull { it.name != null } ?: return function.name to null
//
//    val fieldName = onlyParameter.name!!
//    val field = createField(onlyParameter.type, fieldName, false)
//    return function.name to field
//  }
  private fun orderMetadata(
    queryClass: KClass<out Query<*>>,
    function: KFunction<*>,
    order: Order
  ): Pair<Type, DatabaseQueryMetadata.OrderMetadata> {
    // Size - 1 since `this` is the first parameter.
    check(function.parameters.size - 1 <= 1)
    val onlyParameter = function.parameters.firstOrNull { it.name != null }

    val field = onlyParameter?.name?.let { name ->
      createField(onlyParameter.type, name, onlyParameter.isVararg)
    } ?: Field(name = "Add Order (path=${order.path}, asc=${order.asc})",
        type = Boolean::class.simpleName!!, repeated = false)
    return Type(
        fields = listOf(field)
    ) to DatabaseQueryMetadata.OrderMetadata(
        name = function.name,
        parametersTypeName = "Order/${queryClass.simpleName}/${function.name}",
        path = order.path,
        ascending = order.asc
    )
  }

  private fun selectMetadata(
    queryClass: KClass<out Query<*>>,
    function: KFunction<*>,
    select: Select,
    selectTargetReturnType: KType
  ): Pair<Type, DatabaseQueryMetadata.SelectMetadata> {
    // Size - 1 since `this` is the first parameter.
    check(function.parameters.size - 1 <= 1)
    val onlyParameter = function.parameters.firstOrNull { it.name != null }

    val paths = if (select.path == "") {
      val maybeProjectionClass =
          (selectTargetReturnType.arguments.firstOrNull()?.type?.classifier as KClass<*>?)?.supertypes?.filter { it.classifier as KClass<*> == Projection::class }
              ?.firstOrNull()
      if (maybeProjectionClass != null) {
        val paths = (maybeProjectionClass.classifier as KClass<*>).members.map { member ->
          member.annotations.map {
            if (it.annotationClass == Property::class) {
              (it as Property).path
            } else {
              null
            }
          }.fold(listOf<String>()) { acc, path -> path?.let { acc + it } ?: acc }
        }.reduce { acc, paths -> acc + paths }
        paths
      } else {
        listOf()
      }
    } else {
      listOf(select.path)
    }

    val field = onlyParameter?.name?.let { name ->
      if (onlyParameter.type.classifier as KClass<*> == Session::class) {
        Field(name = "Add Select (paths=$paths)", type = Boolean::class.simpleName!!,
            repeated = false)
      } else {
        createField(onlyParameter.type, name, onlyParameter.isVararg)
      }
    }

    return Type(
        fields = field?.let { listOf(field) } ?: listOf()
    ) to DatabaseQueryMetadata.SelectMetadata(
        name = function.name,
        parametersTypeName = "Select/${queryClass.simpleName}/${function.name}",
        paths = paths
    )
  }

  private fun createField(
    fieldType: KType,
    fieldName: String,
    repeated: Boolean
  ): Field? {
    val fieldClass = fieldType.classifier as KClass<*>
    val maybeCreatePrimitiveField = maybeCreatePrimitiveField(fieldClass, fieldName, repeated)
    return when {
      maybeCreatePrimitiveField != null -> maybeCreatePrimitiveField
      fieldClass == Id::class -> Field(fieldName, Long::class.simpleName!!, repeated = repeated)
      fieldClass == Collection::class -> {
        require(!repeated) { "Unexpected query with nested lists for criteria" }
        val fieldClassParameters = fieldType.arguments
        require(fieldClassParameters.size == 1) {
          "Encountered Wire-generated List without 1 type parameter: $fieldType"
        }
        val listType = fieldClassParameters[0].type!!
        createField(listType, fieldName, true)
      }
      else -> Field(fieldName, fieldClass.qualifiedName!!, repeated)
    }
  }

  private fun <T : DbEntity<T>> KClass<out Query<T>>.criteriaFunctions(): MutableList<KFunction<*>> {
    val allMethods = mutableListOf<KFunction<*>>()
    for (function in declaredMemberFunctions) {
      allMethods += function
    }
    for (supertype in allSupertypes) {
      if (supertype.classifier == Query::class || supertype.classifier == Any::class) continue
      val classifier = supertype.classifier as? KClass<*> ?: continue
      for (function in classifier.declaredMemberFunctions) {
        allMethods += function
      }
    }
    return allMethods
  }

//  inline fun <reified T: DbEntity<T>, Q: KClass<out Query<T>>, AA: KClass<out Annotation>> fromAQuery(): DatabaseQueryMetadata =
//      fromQuery(dbEntityClass = T::class, queryClass = Q::class, accessAnnotationClass = AA::class)
}