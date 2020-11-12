/** @jsx jsx */
import { Card, Classes, Intent, H3, Menu, Tag } from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import { jsx } from "@emotion/core"
import { FlexContainer } from "@misk/core"
import {
  cssCodeTag,
  cssColumn,
  cssFloatLeft,
  cssHeader,
  Metadata
} from "../components"
import { cssMetadataMenu } from "./CommonComponents"

/**
 * Empty Database Query Card UI for use with BlueprintJS Skeleton class in loading UIs
 * https://blueprintjs.com/docs/#core/components/skeleton
 */
export const SkeletonText = () => (
  <span className={Classes.SKELETON}>{"Lorem ipsum"}</span>
)

export const SkeletonDatabaseQueryComponent = () => (
  <Card>
    <div css={cssHeader}>
          <span css={cssFloatLeft}>
            <H3><SkeletonText/></H3>
          </span>
          <Tag
            css={cssFloatLeft}
            intent={Intent.PRIMARY}
            large={true}
          >
            <SkeletonText/>
          </Tag>
          <span css={cssFloatLeft}>
            <Tag css={cssCodeTag} icon={IconNames.TH} large={true}>
              <SkeletonText/>
            </Tag>
          </span>
          <span css={cssFloatLeft}>
            <Tag css={cssCodeTag} icon={IconNames.LOCK} large={true}>
              <SkeletonText/>
            </Tag>
          </span>
        </div>
    <FlexContainer>
      <div css={cssColumn}>
        <Menu css={cssMetadataMenu}>
          <Metadata label={"(0)"} content={"Constraints"} />
          <Metadata label={"(0)"} content={"Orders"} />
          <Metadata label={"(0)"} content={"Selects"} />
        </Menu>
      </div>
      <div css={cssColumn}>
        <Menu css={cssMetadataMenu}>
          <Metadata label={"Services (0)"} content={<SkeletonText />} />
          <Metadata label={"Roles (0)"} content={<SkeletonText />} />
          <Metadata content={"Run a Query"} />
        </Menu>
      </div>
    </FlexContainer>
  </Card>
)
