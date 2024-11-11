import { atom, selector } from "recoil"

import {
  IndexerConfiguration,
  ProductDataFieldConfiguration,
  SearchConfiguration,
} from "@/types/config"
import { Commit } from "@/types/github"

// Indexer & search service configuration states
export const indexerConfigurationState = atom({
  key: "IndexerConfigurationState",
  default: {} as IndexerConfiguration,
})

export const searchConfigurationState = atom({
  key: "SearchConfigurationState",
  default: {} as SearchConfiguration,
})

export const searchConfigurationUpdatesState = atom({
  key: "SearchConfigurationUpdatesState",
  default: 0,
})

export const isSearchConfigurationDirtyState = selector({
  key: "IsSearchConfigurationDirtyState",
  get: ({ get }) => {
    const configurationUpdates = get(searchConfigurationUpdatesState)
    return configurationUpdates > 2
  },
})

export const indexerConfigurationUpdatesState = atom({
  key: "IndexerConfigurationUpdatesState",
  default: 0,
})

export const isIndexerConfigurationDirtyState = selector({
  key: "IsIndexerConfigurationDirtyState",
  get: ({ get }) => {
    const configurationUpdates = get(indexerConfigurationUpdatesState)
    return configurationUpdates > 1
  },
})

export const isConfigurationLoadedState = atom({
  key: "IsConfigurationLoadedState",
  default: false,
})

// Product data field configuration states
export const productDataFieldConfigurationState = atom({
  key: "ProductDataFieldConfigurationState",
  default: [] as ProductDataFieldConfiguration[],
})

// Commit history
export const commitsState = atom({
  key: "CommitsState",
  default: [] as Commit[],
})

export const commitsPageState = atom({
  key: "CommitsPageState",
  default: 1,
})
