"use client"

import { useSearchParams } from "next/navigation"
import _ from "lodash"
import { useRecoilState } from "recoil"

import { QueryProcessingConfigurationItem } from "@/types/config"
import { SearchParamsMap } from "@/types/searchParams"
import {
  isConfigurationLoadedState,
  searchConfigurationState,
} from "@/lib/global-state"
import { Separator } from "@/components/ui/separator"
import { QueryProcessingConfiguration } from "@/components/configuration/query-processing-configuration"
import Loader from "@/components/misc/loader"

export default function QueryProcessingConfigurationSearchSettings() {
  const [isConfigurationLoaded, setIsConfigurationLoadedState] = useRecoilState(
    isConfigurationLoadedState
  )
  const [searchConfiguration, setSearchConfiguration] = useRecoilState(
    searchConfigurationState
  )
  const searchParams = useSearchParams()

  const configParam = searchParams.get(SearchParamsMap.config)

  return (
    <div className="w-full space-y-6">
      <div>
        <h3 className="text-lg font-medium">Query Processing</h3>
        <p className="text-sm text-muted-foreground">
          Manage the query processing configuration of your search service.
        </p>
      </div>
      <Separator />
      {isConfigurationLoaded ? (
        <div className="flex flex-col gap-4">
          <QueryProcessingConfiguration
            onCreate={(item) => {
              const updatedSearchConfiguration =
                _.cloneDeep(searchConfiguration)

              if (configParam) {
                const userQueryPreprocessors =
                  _.get(updatedSearchConfiguration, [
                    "ocs",
                    "tenant-config",
                    configParam,
                    "query-processing",
                    "user-query-preprocessors",
                  ]) ?? []
                _.set(
                  updatedSearchConfiguration,
                  [
                    "ocs",
                    "tenant-config",
                    configParam,
                    "query-processing",
                    "user-query-preprocessors",
                  ],
                  [...userQueryPreprocessors, item.type]
                )

                const pluginConfigurations =
                  _.get(updatedSearchConfiguration, [
                    "ocs",
                    "tenant-config",
                    configParam,
                    "plugin-configuration",
                  ]) ?? []
                _.set(
                  updatedSearchConfiguration,
                  ["ocs", "tenant-config", configParam, "plugin-configuration"],
                  {
                    ...pluginConfigurations,
                    [`[${item.type}]`]: { ...item.options },
                  }
                )
              } else {
                const userQueryPreprocessors =
                  _.get(updatedSearchConfiguration, [
                    "ocs",
                    "default-tenant-config",
                    "query-processing",
                    "user-query-preprocessors",
                  ]) ?? []
                _.set(
                  updatedSearchConfiguration,
                  [
                    "ocs",
                    "default-tenant-config",
                    "query-processing",
                    "user-query-preprocessors",
                  ],
                  [...userQueryPreprocessors, item.type]
                )

                const pluginConfigurations =
                  _.get(updatedSearchConfiguration, [
                    "ocs",
                    "default-tenant-config",
                    "plugin-configuration",
                  ]) ?? []
                _.set(
                  updatedSearchConfiguration,
                  ["ocs", "default-tenant-config", "plugin-configuration"],
                  {
                    ...pluginConfigurations,
                    [`[${item.type}]`]: { ...item.options },
                  }
                )
              }

              setSearchConfiguration(updatedSearchConfiguration)
            }}
            onDelete={(item, index) => {
              const updatedSearchConfiguration =
                _.cloneDeep(searchConfiguration)

              if (configParam) {
                updatedSearchConfiguration.ocs!["tenant-config"]![configParam][
                  "query-processing"
                ]!["user-query-preprocessors"] =
                  updatedSearchConfiguration.ocs!["tenant-config"]![
                    configParam
                  ]["query-processing"]!["user-query-preprocessors"]?.filter(
                    (_, i) => i !== index
                  )

                delete updatedSearchConfiguration.ocs!["tenant-config"]![
                  configParam
                ]?.["plugin-configuration"]?.[`[${item.type}]`]
              } else {
                updatedSearchConfiguration.ocs!["default-tenant-config"]![
                  "query-processing"
                ]!["user-query-preprocessors"] =
                  updatedSearchConfiguration.ocs!["default-tenant-config"]?.[
                    "query-processing"
                  ]!["user-query-preprocessors"]?.filter((_, i) => i !== index)

                delete updatedSearchConfiguration.ocs![
                  "default-tenant-config"
                ]?.["plugin-configuration"]?.[`[${item.type}]`]
              }

              setSearchConfiguration(updatedSearchConfiguration)
            }}
            onEdit={(item, index) => {
              const updatedSearchConfiguration =
                _.cloneDeep(searchConfiguration)

              if (configParam) {
                updatedSearchConfiguration.ocs!["tenant-config"]![configParam]![
                  "query-processing"
                ]!["user-query-preprocessors"]![index] = item.type

                updatedSearchConfiguration.ocs!["tenant-config"]![configParam]![
                  "plugin-configuration"
                ]![`[${item.type}]`]! = item.options ?? {}
              } else {
                updatedSearchConfiguration.ocs!["default-tenant-config"]![
                  "query-processing"
                ]!["user-query-preprocessors"]![index] = item.type

                updatedSearchConfiguration.ocs!["default-tenant-config"]![
                  "plugin-configuration"
                ]![`[${item.type}]`]! = item.options ?? {}
              }

              setSearchConfiguration(updatedSearchConfiguration)
            }}
            items={
              configParam
                ? Object.entries(
                    searchConfiguration?.ocs?.["tenant-config"]?.[
                      configParam
                    ]?.["plugin-configuration"] ?? {}
                  ).map(
                    ([key, value]) =>
                      ({
                        type: key.slice(1, key.length - 1),
                        options: value,
                      } as QueryProcessingConfigurationItem)
                  )
                : Object.entries(
                    searchConfiguration?.ocs?.["default-tenant-config"]?.[
                      "plugin-configuration"
                    ] ?? {}
                  ).map(
                    ([key, value]) =>
                      ({
                        type: key.slice(1, key.length - 1),
                        options: value,
                      } as QueryProcessingConfigurationItem)
                  )
            }
            setItems={(items) => {
              const updatedSearchConfiguration =
                _.cloneDeep(searchConfiguration)

              if (configParam) {
                _.set(
                  updatedSearchConfiguration,
                  [
                    "ocs",
                    "tenant-config",
                    configParam,
                    "query-processing",
                    "user-query-preprocessors",
                  ],
                  items.map((item) => item.type)
                )

                _.set(
                  updatedSearchConfiguration,
                  ["ocs", "tenant-config", configParam, "plugin-configuration"],
                  Object.assign(
                    {},
                    ...items.map((item) => ({
                      [`[${item.type}]`]: { ...item.options },
                    }))
                  )
                )
              } else {
                _.set(
                  updatedSearchConfiguration,
                  [
                    "ocs",
                    "default-tenant-config",
                    "query-processing",
                    "user-query-preprocessors",
                  ],
                  items.map((item) => item.type)
                )

                _.set(
                  updatedSearchConfiguration,
                  ["ocs", "default-tenant-config", "plugin-configuration"],
                  Object.assign(
                    {},
                    ...items.map((item) => ({
                      [`[${item.type}]`]: { ...item.options },
                    }))
                  )
                )
              }

              setSearchConfiguration(updatedSearchConfiguration)
            }}
          />
        </div>
      ) : (
        <div>
          <Loader />
        </div>
      )}
    </div>
  )
}
