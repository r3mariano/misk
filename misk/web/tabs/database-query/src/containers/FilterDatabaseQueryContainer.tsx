import { ControlGroup, InputGroup, Spinner } from "@blueprintjs/core"
import { chain } from "lodash"
import React, { Dispatch, useState, SetStateAction } from "react"
import { handler } from "src/form-builder"
import { IDatabaseQueryMetadataAPI } from "./DatabaseQueryInterfaces"

export const FilterDatabaseQueryContainer = (props: {
  disabled?: boolean
  setFilteredMetadata: Dispatch<SetStateAction<IDatabaseQueryMetadataAPI[]>>
  metadata: IDatabaseQueryMetadataAPI[]
  tag: string
}) => {
  // Initialize input state
  const [filterText, setFilterText] = useState("")

  const filterMetadata = (
    metadata: IDatabaseQueryMetadataAPI[],
    filterValue?: string
  ): any =>
    chain(metadata)
      .filter((dashboardQuery: IDatabaseQueryMetadataAPI) => {
        if (filterValue == null || filterValue === "") {
          return true
        } else {
          return JSON.stringify(dashboardQuery)
            .toString()
            .toLowerCase()
            .includes(filterValue.toLowerCase())
        }
      })
      .value()

  const updateFilterText = (update: string) => {
    setFilterText(update)
    // If filter text reset, load all metadata
    if (update == "") {
      props.setFilteredMetadata(props.metadata)
    } else {
      props.setFilteredMetadata(filterMetadata(props.metadata, filterText))
    }
  }

  return (
    <ControlGroup fill={true}>
      <InputGroup
        disabled={props.disabled}
        large={true}
        onChange={handler.handle(updateFilterText)}
        placeholder={
          props.disabled
            ? "Loading Database Queries..."
            : "Filter Database Queries"
        }
        rightElement={props.disabled ? <Spinner size={20} /> : <span />}
      />
    </ControlGroup>
  )
}
