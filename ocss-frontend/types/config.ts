export type Field = {
  name?: string
  type?: FieldType
  "field-level"?: FieldLevel
  "source-names"?: string[]
  usage?: FieldUsage[]
}

export const fieldTypes = ["string", "number", "category", "id"] as const
export type FieldType = (typeof fieldTypes)[number]

export const fieldLevels = ["both", "variant", "master"] as const
export type FieldLevel = (typeof fieldLevels)[number]

export const fieldUsages = [
  "search",
  "result",
  "sort",
  "facet",
  "score",
] as const
export type FieldUsage = (typeof fieldUsages)[number]

export type IndexSettings = {
  "replica-count"?: number
  "minimum-document-count"?: number
  "refresh-interval"?: string
  "use-default-config"?: boolean
  "wait-time-ms-for-healthy-index"?: number
}

export type FieldConfiguration = {
  fields: { [name: string]: Field }
  "dynamic-fields": Field[]
}

export type IndexConfig = {
  "indexer-settings"?: IndexSettings
  "field-configuration"?: FieldConfiguration
}

export type IndexerConfiguration = {
  ocs: {
    "default-index-config"?: IndexConfig
    "index-config"?: { [name: string]: IndexConfig }
  }
}

export const facetTypes = [
  "term",
  "hierarchical",
  "range",
  "interval_1",
  "interval_5",
  "interval_10",
  "interval_100",
]
export type FacetType = (typeof facetTypes)[number]

export type Facet = {
  label?: string
  "source-field"?: string
  type?: FacetType | string
  "meta-data"?: { [key: string]: object } // TODO: Add metaData form
  "optimal-value-count"?: number
  "show-unselected-options"?: boolean
  "multi-select"?: boolean
  order?: number
  "value-order"?: FacetValueOrder
  excludeFromFacetLimit?: boolean
  "prefer-variant-on-filter"?: boolean
  "min-facet-coverage"?: number
  "min-value-count"?: number
  "filter-dependencies"?: string[]
}

export const facetValueOrders = [
  "COUNT",
  "ALPHANUM_ASC",
  "ALPHANUM_DESC",
] as const
export type FacetValueOrder = (typeof facetValueOrders)[number]

export type SearchConfiguration = {
  ocs: {
    "tenant-config": { [name: string]: SearchConfig }
    "default-tenant-config": SearchConfig
  }
}

export type SearchConfig = {
  "facet-configuration"?: {
    maxFacets: number
    facets: Facet[]
  }
  "sort-configuration"?: SortOption[]
  "scoring-configuration"?: ScoringConfiguration
  "query-processing"?: QueryProcessing
  "plugin-configuration"?: PluginConfiguration

  "use-default-query-config"?: boolean
  "use-default-scoring-config"?: boolean
  "use-default-facet-config"?: boolean
  "use-default-sort-config"?: boolean
}

export type QueryProcessing = {
  "user-query-preprocessors"?: string[]
  "user-query-analyzer"?: string
}

export type PluginConfiguration = {
  [name: string]: {
    [key: string]: string
  }
}

export const pluginConfigurations = [
  {
    label: "Filter character ranges",
    value:
      "de.cxp.ocs.elasticsearch.query.CodePointFilterUserQueryPreprocessor",
  },
  {
    label: "Smart query replacement",
    value: "io.searchhub.smartquery.SmartQueryPreprocessor",
  },
  {
    label: "Strip non-alphanumeric characters",
    value: "de.cxp.ocs.elasticsearch.query.NonAlphanumericStripPreprocessor",
  },
  {
    label: "ASCIIfy user query",
    value: "de.cxp.ocs.elasticsearch.query.AsciifyUserQueryPreprocessor",
  },
  {
    label: "Custom preprocessor",
    value: "custom",
  },
]

export const pluginConfigurationValues = [
  "de.cxp.ocs.elasticsearch.query.CodePointFilterUserQueryPreprocessor",
  "io.searchhub.smartquery.SmartQueryPreprocessor",
  "de.cxp.ocs.elasticsearch.query.NonAlphanumericStripPreprocessor",
  "de.cxp.ocs.elasticsearch.query.AsciifyUserQueryPreprocessor",
  "custom",
] as const
export type PluginConfigurationValues =
  (typeof pluginConfigurationValues)[number]

export type QueryProcessingConfigurationItem = {
  type: string
  options?: {
    code_point_lower_bound?: string
    code_point_upper_bound?: string

    apiKey?: string
    tenant?: string
  }
}

export type SortOption = {
  label: string
  field: string
  order?: SortOptionOrder
  missing?: SortOptionMissing
}

export const sortOptionOrders = ["ASC", "DESC"] as const
export type SortOptionOrder = (typeof sortOptionOrders)[number]

export const sortOptionMissings = ["_first", "_last"] as const
export type SortOptionMissing = (typeof sortOptionMissings)[number]

export type ScoringConfiguration = {
  "boost-mode": BoostMode
  "score-mode": ScoreMode
  "score-functions": ScoreFunction[]
}

export const boostModes = [
  "multiply",
  "avg",
  "sum",
  "min",
  "max",
  "replace",
] as const
export type BoostMode = (typeof boostModes)[number]

export const scoreModes = [
  "multiply",
  "avg",
  "sum",
  "min",
  "max",
  "first",
] as const
export type ScoreMode = (typeof scoreModes)[number]

export type ScoreFunction = {
  field?: string
  type?: ScoreFunctionType
  weight?: number
  options?: {
    USE_FOR_VARIANTS?: boolean
    RANDOM_SEED?: string
    MISSING?: any
    MODIFIER?: ScoreFunctionModifier
    FACTOR?: number
    SCRIPT_CODE?: string
    ORIGIN?: number
    SCALE?: number
    OFFSET?: number
    DECAY?: number
  }
}

export const scoreFunctionTypes = [
  "WEIGHT",
  "RANDOM_SCORE",
  "FIELD_VALUE_FACTOR",
  "SCRIPT_SCORE",
  "DECAY_GAUSS",
  "DECAY_LINEAR",
  "DECAY_EXP",
] as const
export type ScoreFunctionType = (typeof scoreFunctionTypes)[number]

export const scoreFunctionModifiers = [
  "none",
  "log",
  "log1p",
  "log2p",
  "ln",
  "ln1p",
  "ln2p",
  "square",
  "sqrt",
  "reciprocal",
] as const
export type ScoreFunctionModifier = (typeof scoreFunctionModifiers)[number]

// Product card editor
export type ProductDataFieldConfiguration = {
  sourceField: string
  type: ProductDataFieldConfigurationType
  style: ProductDataFieldConfigurationStyle
  currency?: ProductDataFieldConfigurationCurrency
  divisor?: number
}

export const productDataFieldConfigurationTypes = [
  "string",
  "image",
  "price",
] as const
export type ProductDataFieldConfigurationType =
  (typeof productDataFieldConfigurationTypes)[number]

export const productDataFieldConfigurationStyles = [
  "regular",
  "bold",
  "small",
] as const
export type ProductDataFieldConfigurationStyle =
  (typeof productDataFieldConfigurationStyles)[number]

export const productDataFieldConfigurationCurrencies = [
  "€",
  "$",
  "¥",
  "£",
] as const
export type ProductDataFieldConfigurationCurrency =
  (typeof productDataFieldConfigurationCurrencies)[number]
