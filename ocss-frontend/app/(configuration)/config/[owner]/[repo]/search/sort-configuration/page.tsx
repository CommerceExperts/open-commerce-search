"use client"

import { useSearchParams } from "next/navigation"
import _ from "lodash"
import { useRecoilState } from "recoil"

import { SearchParamsMap } from "@/types/searchParams"
import {
  indexerConfigurationState,
  isConfigurationLoadedState,
  searchConfigurationState,
} from "@/lib/global-state"
import { Separator } from "@/components/ui/separator"
import UseDefaultConfigSwitch from "@/components/ui/use-default-config-switch"
import { SortConfiguration } from "@/components/configuration/sort-configuration"
import Loader from "@/components/misc/loader"

export default function SortConfigurationSearchSettings() {
  const [isConfigurationLoaded, setIsConfigurationLoadedState] = useRecoilState(
    isConfigurationLoadedState
  )
  const [searchConfiguration, setSearchConfiguration] = useRecoilState(
    searchConfigurationState
  )
  const [indexerConfiguration, setIndexerConfiguration] = useRecoilState(
    indexerConfigurationState
  )
  const searchParams = useSearchParams()

  const configParam = searchParams.get(SearchParamsMap.config)

  return (
    <div className="w-full space-y-6">
      <div>
        <h3 className="text-lg font-medium">Sorting</h3>
        <p className="text-sm text-muted-foreground">
          Manage the sort options of your search service.
        </p>
      </div>
      <Separator />
      {isConfigurationLoaded ? (
        <div className="flex flex-col gap-4">
          {configParam && (
            <UseDefaultConfigSwitch
              checked={
                searchConfiguration.ocs["tenant-config"]?.[configParam]?.[
                  "use-default-sort-config"
                ] ?? false
              }
              onCheckedChange={(useDefaultConfig) => {
                const updatedSearchConfiguration =
                  _.cloneDeep(searchConfiguration)

                _.set(
                  updatedSearchConfiguration,
                  [
                    "ocs",
                    "tenant-config",
                    configParam,
                    "use-default-sort-config",
                  ],
                  useDefaultConfig
                )

                setSearchConfiguration(updatedSearchConfiguration)
              }}
            />
          )}

          <SortConfiguration
            onSortOptionCreation={(newSortOption) => {
              const updatedSearchConfiguration =
                _.cloneDeep(searchConfiguration)

              if (configParam) {
                const sortOptions =
                  _.get(updatedSearchConfiguration, [
                    "ocs",
                    "tenant-config",
                    configParam,
                    "sort-configuration",
                  ]) ?? []

                _.set(
                  updatedSearchConfiguration,
                  ["ocs", "tenant-config", configParam, "sort-configuration"],
                  [...sortOptions, newSortOption]
                )
              } else {
                const sortOptions =
                  _.get(updatedSearchConfiguration, [
                    "ocs",
                    "default-tenant-config",
                    "sort-configuration",
                  ]) ?? []

                _.set(
                  updatedSearchConfiguration,
                  ["ocs", "default-tenant-config", "sort-configuration"],
                  [...sortOptions, newSortOption]
                )
              }

              setSearchConfiguration(updatedSearchConfiguration)
            }}
            onSortOptionEdit={(newSortOption, index) => {
              const updatedSearchConfiguration =
                _.cloneDeep(searchConfiguration)

              if (configParam) {
                updatedSearchConfiguration.ocs!["tenant-config"]![configParam][
                  "sort-configuration"
                ]![index] = newSortOption
              } else {
                updatedSearchConfiguration.ocs!["default-tenant-config"]![
                  "sort-configuration"
                ]![index] = newSortOption
              }

              setSearchConfiguration(updatedSearchConfiguration)
            }}
            onSortOptionDelete={(sortOption, index) => {
              const updatedSearchConfiguration =
                _.cloneDeep(searchConfiguration)

              if (configParam) {
                updatedSearchConfiguration.ocs!["tenant-config"]![configParam][
                  "sort-configuration"
                ] = updatedSearchConfiguration.ocs!["tenant-config"]![
                  configParam
                ]?.["sort-configuration"]?.filter(
                  (_sortOption, _index) => index !== _index
                )
              } else {
                updatedSearchConfiguration.ocs!["default-tenant-config"][
                  "sort-configuration"
                ] = updatedSearchConfiguration.ocs!["default-tenant-config"]?.[
                  "sort-configuration"
                ]?.filter((_sortOption, _index) => index !== _index)
              }

              setSearchConfiguration(updatedSearchConfiguration)
            }}
            sortOptions={
              (configParam
                ? searchConfiguration.ocs?.["tenant-config"]?.[configParam]?.[
                    "sort-configuration"
                  ] ?? []
                : searchConfiguration.ocs?.["default-tenant-config"]?.[
                    "sort-configuration"
                  ]) ?? []
            }
            fields={
              configParam
                ? Object.values(
                    indexerConfiguration?.ocs?.["index-config"]?.[
                      configParam
                    ]?.["field-configuration"]?.fields ?? {}
                  ).concat(
                    Object.values(
                      indexerConfiguration?.ocs?.["index-config"]?.[
                        configParam
                      ]?.["field-configuration"]?.["dynamic-fields"] ?? {}
                    )
                  )
                : Object.values(
                    indexerConfiguration?.ocs!["default-index-config"]?.[
                      "field-configuration"
                    ]?.fields ?? {}
                  ).concat(
                    Object.values(
                      indexerConfiguration?.ocs!["default-index-config"]?.[
                        "field-configuration"
                      ]?.["dynamic-fields"] ?? {}
                    )
                  )
            }
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
