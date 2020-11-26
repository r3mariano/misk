/** @jsx jsx */
import { Button, Callout, Drawer, H1 } from "@blueprintjs/core"
import { jsx } from "@emotion/core"
import { CodePreContainer } from "@misk/core"
import { useState } from "react"
import { cssFloatLeft, cssHeader } from "../components"

export const TabHeader = () => {
  const [isOpenInstructions, setIsOpenInstructions] = useState(false)

  return (
    <span css={cssHeader}>
      <span css={cssFloatLeft}>
        <H1>Database Query</H1>
      </span>
      <Button
        active={isOpenInstructions}
        css={cssFloatLeft}
        onClick={() => setIsOpenInstructions(!isOpenInstructions)}
      >
        {"Install Instructions"}
      </Button>
      <Drawer
        isOpen={isOpenInstructions}
        onClose={() => setIsOpenInstructions(false)}
        title={"Install Instructions"}
      >
        <Callout
          title={
            "Install and configure misk-hibernate.HibernateDatabaseQueryMetadataModule with your entities and queries to use this tab"
          }
        >
          <CodePreContainer>
            {`// YourServicePersistenceModule.kt

class YourServicePersistenceModule(
  private val config: YourServiceConfig,
  private val dataSourceName: String,
  private val databasePool: DatabasePool = RealDatabasePool
) : KAbstractModule() {
  override fun configure() {
    install(SkimHibernateModule(...))
    install(object : HibernateEntityModule(YourServiceDbCluster::class) { ... }


    // Makes Queries and Entities available in Misk Admin Dashboard
    install(object: HibernateDatabaseQueryMetadataModule() {
      override fun configureHibernate() {
        addQuery(DbDesignStudio::class, DbDesignStudioQuery::class)
        addQuery(DbSmsBlockedNumber::class, DbSmsBlockedNumberQuery::class, DatabaseQueryAdminDashboardAccess::class)
        addQuery(DbTemplate::class, DbTemplateQuery::class, DatabaseQueryAdminDashboardAccess::class)
      }
    })


  }
}`}
          </CodePreContainer>
        </Callout>
      </Drawer>
    </span>
  )
}
