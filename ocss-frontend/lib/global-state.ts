import { atom } from "jotai"

import {
  IndexerConfiguration,
  ProductDataFieldConfiguration,
  SearchConfiguration,
} from "@/types/config"
import { Commit } from "@/types/github"

// Indexer & search service configuration states
export const indexerConfigurationState = atom({} as IndexerConfiguration)

export const searchConfigurationState = atom({} as SearchConfiguration)

export const searchConfigurationUpdatesState = atom(0)

export const isSearchConfigurationDirtyState = atom((get) => {
  const configurationUpdates = get(searchConfigurationUpdatesState)
  return configurationUpdates > 2
})

export const indexerConfigurationUpdatesState = atom(0)

export const isIndexerConfigurationDirtyState = atom((get) => {
  const configurationUpdates = get(indexerConfigurationUpdatesState)
  return configurationUpdates > 1
})

export const isConfigurationLoadedState = atom(false)

// Product data field configuration states
export const productDataFieldConfigurationState = atom(
  [] as ProductDataFieldConfiguration[]
)

// Commit history
export const commitsState = atom([] as Commit[])

export const commitsPageState = atom(1)
