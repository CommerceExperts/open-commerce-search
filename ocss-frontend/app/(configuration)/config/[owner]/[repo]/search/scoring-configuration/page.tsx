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
import BoostModeConfiguration from "@/components/configuration/boost-mode-configuration"
import ScoreModeConfiguration from "@/components/configuration/score-mode-configuration"
import { ScoringConfiguration } from "@/components/configuration/scoring-configuration"
import Loader from "@/components/misc/loader"

export default function ScoringConfigurationSearchSettings() {
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
        <h3 className="text-lg font-medium">Scoring</h3>
        <p className="text-sm text-muted-foreground">
          Manage the scoring configuration of your search service.
        </p>
      </div>
      <Separator />
      {isConfigurationLoaded ? (
        <div className="flex flex-col gap-4">
          {configParam && (
            <UseDefaultConfigSwitch
              checked={
                searchConfiguration.ocs["tenant-config"]?.[configParam]?.[
                  "use-default-scoring-config"
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
                    "use-default-scoring-config",
                  ],
                  useDefaultConfig
                )

                setSearchConfiguration(updatedSearchConfiguration)
              }}
            />
          )}

          <BoostModeConfiguration
            boostMode={
              configParam
                ? searchConfiguration?.ocs?.["tenant-config"]?.[configParam][
                    "scoring-configuration"
                  ]?.["boost-mode"]
                : searchConfiguration?.ocs?.["default-tenant-config"]?.[
                    "scoring-configuration"
                  ]?.["boost-mode"]
            }
            onBoostModeChange={(boostMode) => {
              const updatedSearchConfiguration =
                _.cloneDeep(searchConfiguration)

              if (configParam) {
                _.set(
                  updatedSearchConfiguration,
                  [
                    "ocs",
                    "tenant-config",
                    configParam,
                    "scoring-configuration",
                    "boost-mode",
                  ],
                  boostMode
                )
              } else {
                _.set(
                  updatedSearchConfiguration,
                  [
                    "ocs",
                    "default-tenant-config",
                    "scoring-configuration",
                    "boost-mode",
                  ],
                  boostMode
                )
              }

              setSearchConfiguration(updatedSearchConfiguration)
            }}
          />
          <ScoreModeConfiguration
            scoreMode={
              configParam
                ? searchConfiguration?.ocs?.["tenant-config"]?.[configParam][
                    "scoring-configuration"
                  ]?.["score-mode"]
                : searchConfiguration?.ocs?.["default-tenant-config"]?.[
                    "scoring-configuration"
                  ]?.["score-mode"]
            }
            onScoreModeChange={(scoreMode) => {
              const updatedSearchConfiguration =
                _.cloneDeep(searchConfiguration)

              if (configParam) {
                _.set(
                  updatedSearchConfiguration,
                  [
                    "ocs",
                    "tenant-config",
                    configParam,
                    "scoring-configuration",
                    "score-mode",
                  ],
                  scoreMode
                )
              } else {
                _.set(
                  updatedSearchConfiguration,
                  [
                    "ocs",
                    "default-tenant-config",
                    "scoring-configuration",
                    "score-mode",
                  ],
                  scoreMode
                )
              }

              setSearchConfiguration(updatedSearchConfiguration)
            }}
          />

          <ScoringConfiguration
            onScoreFunctionCreation={(newScoreFunction) => {
              const updatedSearchConfiguration =
                _.cloneDeep(searchConfiguration)

              if (configParam) {
                const scoreFunctions =
                  _.get(updatedSearchConfiguration, [
                    "ocs",
                    "tenant-config",
                    configParam,
                    "scoring-configuration",
                    "score-functions",
                  ]) ?? []

                _.set(
                  updatedSearchConfiguration,
                  [
                    "ocs",
                    "tenant-config",
                    configParam,
                    "scoring-configuration",
                    "score-functions",
                  ],
                  [...scoreFunctions, newScoreFunction]
                )
              } else {
                const scoreFunctions =
                  _.get(updatedSearchConfiguration, [
                    "ocs",
                    "default-tenant-config",
                    "scoring-configuration",
                    "score-functions",
                  ]) ?? []

                _.set(
                  updatedSearchConfiguration,
                  [
                    "ocs",
                    "default-tenant-config",
                    "scoring-configuration",
                    "score-functions",
                  ],
                  [...scoreFunctions, newScoreFunction]
                )
              }

              setSearchConfiguration(updatedSearchConfiguration)
            }}
            onScoreFunctionDelete={(scoreFunction, index) => {
              const updatedSearchConfiguration =
                _.cloneDeep(searchConfiguration)

              if (configParam) {
                updatedSearchConfiguration.ocs!["tenant-config"]![configParam][
                  "scoring-configuration"
                ]!["score-functions"] = updatedSearchConfiguration.ocs![
                  "tenant-config"
                ]![configParam]?.["scoring-configuration"]![
                  "score-functions"
                ]?.filter((_, i) => i !== index)
              } else {
                updatedSearchConfiguration.ocs!["default-tenant-config"][
                  "scoring-configuration"
                ]!["score-functions"] = updatedSearchConfiguration.ocs![
                  "default-tenant-config"
                ]?.["scoring-configuration"]!["score-functions"]?.filter(
                  (_, i) => i !== index
                )
              }

              setSearchConfiguration(updatedSearchConfiguration)
            }}
            onScoreFunctionEdit={(scoreFunction, index) => {
              const updatedSearchConfiguration =
                _.cloneDeep(searchConfiguration)

              if (configParam) {
                updatedSearchConfiguration.ocs!["tenant-config"]![configParam][
                  "scoring-configuration"
                ]!["score-functions"]![index] = scoreFunction
              } else {
                updatedSearchConfiguration.ocs!["default-tenant-config"]![
                  "scoring-configuration"
                ]!["score-functions"]![index] = scoreFunction
              }

              setSearchConfiguration(updatedSearchConfiguration)
            }}
            scoreFunctions={
              configParam
                ? searchConfiguration?.ocs?.["tenant-config"]?.[configParam][
                    "scoring-configuration"
                  ]?.["score-functions"]
                : searchConfiguration?.ocs?.["default-tenant-config"]?.[
                    "scoring-configuration"
                  ]?.["score-functions"]
            }
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
        </div>
      ) : (
        <div>
          <Loader />
        </div>
      )}
    </div>
  )
}
