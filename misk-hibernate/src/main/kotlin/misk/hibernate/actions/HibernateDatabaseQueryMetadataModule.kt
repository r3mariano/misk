package misk.hibernate.actions

import misk.hibernate.DbEntity
import misk.hibernate.HibernateEntity
import misk.hibernate.Query
import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.metadata.DatabaseQueryMetadata
import kotlin.reflect.KClass

abstract class HibernateDatabaseQueryMetadataModule : KAbstractModule() {
  abstract fun configureHibernate()

  protected fun <T : DbEntity<T>> addQuery(
    dbEntityClass: KClass<T>,
    queryClass: KClass<out Query<T>>? = null,
    accessAnnotationClass: KClass<out Annotation>? = null
  ) = apply {
    multibind<DatabaseQueryMetadata>().toProvider(DatabaseQueryMetadataProvider(
        dbEntityClass = dbEntityClass,
        queryClass = queryClass,
        accessAnnotationClass = accessAnnotationClass
    ))
    if (queryClass != null) {
      multibind<HibernateQuery>().toInstance(HibernateQuery(queryClass as KClass<out Query<DbEntity<*>>>))
    }
  }

  /** Adds a DbEntity to Database-Query Admin Dashboard Tab with a static Misk.Query */
  protected inline fun <reified T : DbEntity<T>, reified Q: Query<T>, reified AA: Annotation> addQuery() {
    addQuery(T::class, Q::class, AA::class)
  }

  /** Adds a DbEntity to Database-Query Admin Dashboard Tab with a dynamic query (not a static Misk.Query) */
  protected inline fun <reified T : DbEntity<T>, reified AA: Annotation> addDynamicOnlyQuery() {
    addQuery(T::class, null, AA::class)
  }

  override fun configure() {
    newMultibinder<HibernateEntity>()
    newMultibinder<DatabaseQueryMetadata>()

    configureHibernate()

    install(WebActionModule.create<HibernateDatabaseQueryDynamicAction>())
    install(WebActionModule.create<HibernateDatabaseQueryStaticAction>())
  }
}