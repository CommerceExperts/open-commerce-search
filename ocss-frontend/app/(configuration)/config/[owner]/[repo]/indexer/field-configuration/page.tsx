"use client"

import { useSearchParams } from "next/navigation"
import _ from "lodash"
import { useRecoilState } from "recoil"

import { SearchParamsMap } from "@/types/searchParams"
import {
  indexerConfigurationState,
  isConfigurationLoadedState,
} from "@/lib/global-state"
import { Separator } from "@/components/ui/separator"
import { FieldConfiguration } from "@/components/configuration/field-configuration"
import Loader from "@/components/misc/loader"

export default function FieldConfigurationIndexerSettings() {
  const [isConfigurationLoaded, setIsConfigurationLoadedState] = useRecoilState(
    isConfigurationLoadedState
  )
  const [indexerConfiguration, setIndexerConfiguration] = useRecoilState(
    indexerConfigurationState
  )
  const searchParams = useSearchParams()

  const configParam = searchParams.get(SearchParamsMap.config)

  return (
    <div className="space-y-6">
      <div>
        <h3 className="text-lg font-medium">Fields</h3>
        <p className="text-sm text-muted-foreground">
          Manage the fields of your indexer service.
        </p>
      </div>
      <Separator />
      {isConfigurationLoaded ? (
        <div className="flex flex-col gap-4">
          <FieldConfiguration
            heading="Fields"
            onFieldCreation={(newField) => {
              const updatedIndexerConfiguration =
                _.cloneDeep(indexerConfiguration)

              if (configParam) {
                _.set(
                  updatedIndexerConfiguration,
                  [
                    "ocs",
                    "index-config",
                    configParam,
                    "field-configuration",
                    "fields",
                    newField.name!,
                  ],
                  newField
                )
              } else {
                _.set(
                  updatedIndexerConfiguration,
                  [
                    "ocs",
                    "default-index-config",
                    "field-configuration",
                    "fields",
                    newField.name!,
                  ],
                  newField
                )
              }

              setIndexerConfiguration(updatedIndexerConfiguration)
            }}
            onFieldDelete={(field, index) => {
              const updatedIndexerConfiguration =
                _.cloneDeep(indexerConfiguration)

              if (configParam) {
                const oldFieldKey = Object.keys(
                  updatedIndexerConfiguration.ocs!["index-config"]![
                    configParam
                  ]["field-configuration"]!.fields
                ).find((_, _index) => index == _index)

                if (oldFieldKey) {
                  _.unset(updatedIndexerConfiguration, [
                    "ocs",
                    "index-config",
                    configParam,
                    "field-configuration",
                    "fields",
                    oldFieldKey,
                  ])
                }
              } else {
                const oldFieldKey = Object.keys(
                  updatedIndexerConfiguration.ocs!["default-index-config"]![
                    "field-configuration"
                  ]!.fields
                ).find((_, _index) => index == _index)

                if (oldFieldKey) {
                  _.unset(updatedIndexerConfiguration, [
                    "ocs",
                    "default-index-config",
                    "field-configuration",
                    "fields",
                    oldFieldKey,
                  ])
                }
              }

              setIndexerConfiguration(updatedIndexerConfiguration)
            }}
            onFieldEdit={(newField, index) => {
              const updatedIndexerConfiguration =
                _.cloneDeep(indexerConfiguration)

              if (configParam) {
                const oldFieldKey = Object.keys(
                  updatedIndexerConfiguration.ocs!["index-config"]![
                    configParam
                  ]["field-configuration"]!.fields
                ).find((_, _index) => index == _index)

                if (oldFieldKey) {
                  _.unset(updatedIndexerConfiguration, [
                    "ocs",
                    "index-config",
                    configParam,
                    "field-configuration",
                    "fields",
                    oldFieldKey,
                  ])

                  updatedIndexerConfiguration.ocs!["index-config"]![
                    configParam
                  ]["field-configuration"]!.fields[newField.name!] = newField
                }
              } else {
                const oldFieldKey = Object.keys(
                  updatedIndexerConfiguration.ocs!["default-index-config"]![
                    "field-configuration"
                  ]!.fields
                ).find((_, _index) => index == _index)

                if (oldFieldKey) {
                  _.unset(updatedIndexerConfiguration, [
                    "ocs",
                    "default-index-config",
                    "field-configuration",
                    "fields",
                    oldFieldKey,
                  ])

                  updatedIndexerConfiguration.ocs!["default-index-config"]![
                    "field-configuration"
                  ]!.fields[newField.name!] = newField
                }
              }

              setIndexerConfiguration(updatedIndexerConfiguration)
            }}
            fields={
              configParam
                ? Object.values(
                    indexerConfiguration?.ocs?.["index-config"]?.[
                      configParam
                    ]?.["field-configuration"]?.fields ?? {}
                  )
                : Object.values(
                    indexerConfiguration?.ocs!["default-index-config"]?.[
                      "field-configuration"
                    ]?.fields ?? {}
                  )
            }
          />

          <FieldConfiguration
            heading="Dynamic Fields"
            onFieldCreation={(newField) => {
              const updatedIndexerConfiguration =
                _.cloneDeep(indexerConfiguration)

              if (configParam) {
                const dynamicFields =
                  _.get(updatedIndexerConfiguration, [
                    "ocs",
                    "index-config",
                    configParam,
                    "field-configuration",
                    "dynamic-fields",
                  ]) ?? []

                _.set(
                  updatedIndexerConfiguration,
                  [
                    "ocs",
                    "index-config",
                    configParam,
                    "field-configuration",
                    "dynamic-fields",
                  ],
                  [...dynamicFields, newField]
                )
              } else {
                const dynamicFields =
                  _.get(updatedIndexerConfiguration, [
                    "ocs",
                    "default-index-config",
                    "field-configuration",
                    "dynamic-fields",
                  ]) ?? []

                _.set(
                  updatedIndexerConfiguration,
                  [
                    "ocs",
                    "default-index-config",
                    "field-configuration",
                    "dynamic-fields",
                  ],
                  [...dynamicFields, newField]
                )
              }

              setIndexerConfiguration(updatedIndexerConfiguration)
            }}
            onFieldDelete={(field, index) => {
              const updatedIndexerConfiguration =
                _.cloneDeep(indexerConfiguration)

              if (configParam) {
                updatedIndexerConfiguration.ocs!["index-config"]![configParam][
                  "field-configuration"
                ]!["dynamic-fields"] = updatedIndexerConfiguration.ocs![
                  "index-config"
                ]![configParam]?.["field-configuration"]![
                  "dynamic-fields"
                ].filter((_field, _index) => index !== _index)
              } else {
                updatedIndexerConfiguration.ocs!["default-index-config"]![
                  "field-configuration"
                ]!["dynamic-fields"] = updatedIndexerConfiguration.ocs![
                  "default-index-config"
                ]!["field-configuration"]!["dynamic-fields"].filter(
                  (_field, _index) => index !== _index
                )
              }

              setIndexerConfiguration(updatedIndexerConfiguration)
            }}
            onFieldEdit={(newField, index) => {
              const updatedIndexerConfiguration =
                _.cloneDeep(indexerConfiguration)

              if (configParam) {
                updatedIndexerConfiguration.ocs!["index-config"]![configParam][
                  "field-configuration"
                ]!["dynamic-fields"][index] = newField
              } else {
                updatedIndexerConfiguration.ocs!["default-index-config"]![
                  "field-configuration"
                ]!["dynamic-fields"][index] = newField
              }

              setIndexerConfiguration(updatedIndexerConfiguration)
            }}
            fields={
              (configParam
                ? indexerConfiguration.ocs!["index-config"]?.[configParam]?.[
                    "field-configuration"
                  ]?.["dynamic-fields"]
                : indexerConfiguration.ocs!["default-index-config"]?.[
                    "field-configuration"
                  ]?.["dynamic-fields"]) ?? []
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
