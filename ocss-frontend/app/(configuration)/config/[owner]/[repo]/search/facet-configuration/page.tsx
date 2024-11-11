"use client"

import { useSearchParams } from "next/navigation"
import _ from "lodash"
import { useRecoilState } from "recoil"

import { Facet, FieldUsage } from "@/types/config"
import { SearchParamsMap } from "@/types/searchParams"
import {
  indexerConfigurationState,
  isConfigurationLoadedState,
  searchConfigurationState,
} from "@/lib/global-state"
import { Separator } from "@/components/ui/separator"
import UseDefaultConfigSwitch from "@/components/ui/use-default-config-switch"
import { FacetConfiguration } from "@/components/configuration/facet-configuration"
import MaxFacetsConfiguration from "@/components/configuration/max-facets-configuration"
import Loader from "@/components/misc/loader"

export default function FacetConfigurationSearchSettings() {
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

  const fields = (
    configParam
      ? Object.values(
          indexerConfiguration?.ocs?.["index-config"]?.[configParam]?.[
            "field-configuration"
          ]?.fields ?? {}
        )
      : Object.values(
          indexerConfiguration?.ocs?.["default-index-config"]?.[
            "field-configuration"
          ]?.fields ?? {}
        )
  ).filter((field) =>
    field.usage
      ?.map((usage) => usage.trim().toLowerCase())
      .includes("facet" as FieldUsage)
  )

  return (
    <div className="w-full space-y-6">
      <div>
        <h3 className="text-lg font-medium">Facets</h3>
        <p className="text-sm text-muted-foreground">
          Manage the facets of your search service.
        </p>
      </div>
      <Separator />
      {isConfigurationLoaded ? (
        <div className="flex flex-col gap-4">
          {configParam && (
            <UseDefaultConfigSwitch
              checked={
                searchConfiguration.ocs["tenant-config"]?.[configParam]?.[
                  "use-default-facet-config"
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
                    "use-default-facet-config",
                  ],
                  useDefaultConfig
                )

                setSearchConfiguration(updatedSearchConfiguration)
              }}
            />
          )}
          <MaxFacetsConfiguration
            value={
              configParam
                ? searchConfiguration.ocs?.["tenant-config"]?.[configParam]?.[
                    "facet-configuration"
                  ]?.maxFacets
                : searchConfiguration.ocs?.["default-tenant-config"]?.[
                    "facet-configuration"
                  ]?.maxFacets
            }
            onValueChange={(value) => {
              const updatedSearchConfiguration =
                _.cloneDeep(searchConfiguration)

              const parsedValue = parseInt(value)

              if (configParam) {
                _.set(
                  updatedSearchConfiguration,
                  [
                    "ocs",
                    "tenant-config",
                    configParam,
                    "facet-configuration",
                    "maxFacets",
                  ],
                  isNaN(parsedValue) ? 0 : parsedValue
                )
              } else {
                _.set(
                  updatedSearchConfiguration,
                  [
                    "ocs",
                    "default-tenant-config",
                    "facet-configuration",
                    "maxFacets",
                  ],
                  isNaN(parsedValue) ? 0 : parsedValue
                )
              }

              setSearchConfiguration(updatedSearchConfiguration)
            }}
          />
          <FacetConfiguration
            onFacetCreation={(newFacet) => {
              const updatedSearchConfiguration =
                _.cloneDeep(searchConfiguration)

              if (configParam) {
                const facets =
                  _.get(updatedSearchConfiguration, [
                    "ocs",
                    "tenant-config",
                    configParam,
                    "facet-configuration",
                    "facets",
                  ]) ?? []

                _.set(
                  updatedSearchConfiguration,
                  [
                    "ocs",
                    "tenant-config",
                    configParam,
                    "facet-configuration",
                    "facets",
                  ],
                  [...facets, newFacet].map(
                    (facet, i) =>
                      ({ ...facet, order: i + 1 } as unknown as Facet)
                  )
                )
              } else {
                const facets =
                  _.get(updatedSearchConfiguration, [
                    "ocs",
                    "default-tenant-config",
                    "facet-configuration",
                    "facets",
                  ]) ?? []

                _.set(
                  updatedSearchConfiguration,
                  [
                    "ocs",
                    "default-tenant-config",
                    "facet-configuration",
                    "facets",
                  ],
                  [...facets, newFacet].map(
                    (facet, i) =>
                      ({ ...facet, order: i + 1 } as unknown as Facet)
                  )
                )
              }

              setSearchConfiguration(updatedSearchConfiguration)
            }}
            onFacetDelete={(facet, index) => {
              const updatedSearchConfiguration =
                _.cloneDeep(searchConfiguration)

              if (configParam) {
                updatedSearchConfiguration.ocs!["tenant-config"]![configParam][
                  "facet-configuration"
                ]!["facets"] = updatedSearchConfiguration
                  .ocs!["tenant-config"]![configParam]?.[
                    "facet-configuration"
                  ]!["facets"].filter((_facet, _index) => index !== _index)
                  .map(
                    (facet, i) =>
                      ({ ...facet, order: i + 1 } as unknown as Facet)
                  )
              } else {
                updatedSearchConfiguration.ocs!["default-tenant-config"][
                  "facet-configuration"
                ]!["facets"] = updatedSearchConfiguration
                  .ocs!["default-tenant-config"]?.["facet-configuration"]![
                    "facets"
                  ].filter((_facet, _index) => index !== _index)
                  .map(
                    (facet, i) =>
                      ({ ...facet, order: i + 1 } as unknown as Facet)
                  )
              }

              setSearchConfiguration(updatedSearchConfiguration)
            }}
            onFacetEdit={(newFacet, index) => {
              const updatedSearchConfiguration =
                _.cloneDeep(searchConfiguration)

              if (configParam) {
                updatedSearchConfiguration.ocs!["tenant-config"]![configParam][
                  "facet-configuration"
                ]!["facets"][index] = newFacet
              } else {
                updatedSearchConfiguration.ocs!["default-tenant-config"]![
                  "facet-configuration"
                ]!["facets"][index] = newFacet
              }

              setSearchConfiguration(updatedSearchConfiguration)
            }}
            fields={fields}
            setFacets={(facets) => {
              const updatedSearchConfiguration =
                _.cloneDeep(searchConfiguration)

              facets = facets.map((facet, i) => ({ ...facet, order: i + 1 }))

              if (configParam) {
                _.set(
                  updatedSearchConfiguration,
                  [
                    "ocs",
                    "tenant-config",
                    configParam,
                    "facet-configuration",
                    "facets",
                  ],
                  facets
                )
              } else {
                _.set(
                  updatedSearchConfiguration,
                  [
                    "ocs",
                    "default-tenant-config",
                    "facet-configuration",
                    "facets",
                  ],
                  facets
                )
              }

              setSearchConfiguration(updatedSearchConfiguration)
            }}
            facets={
              (configParam
                ? searchConfiguration.ocs?.["tenant-config"]?.[configParam]?.[
                    "facet-configuration"
                  ]?.facets ?? []
                : searchConfiguration.ocs?.["default-tenant-config"]?.[
                    "facet-configuration"
                  ]?.facets) ?? []
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
