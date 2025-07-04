export type FieldType = "STRING" | "NUMBER" | "CATEGORY" | "RAW" | "ID";

export type FieldLevel = "MASTER" | "VARIANT" | "BOTH";

export type FieldUsage =
  | "SEARCH"
  | "RESULT"
  | "SORT"
  | "FACET"
  | "FILTER"
  | "SCORE";

export interface Field {
  name: string;
  type?: FieldType;
  fieldLevel?: FieldLevel;
  sourceNames?: string[];
  usage?: FieldUsage[];
  valueDelimiter?: string | null;
  searchContentPrefix?: string | null;
}

export interface IndexSettings {
  replicaCount?: number;
  refreshInterval?: string;
  minimumDocumentCount?: number;
  waitTimeMsForHealthyIndex?: number;
  useDefaultConfig?: boolean;
}

export interface DataProcessorConfiguration {
  processors?: string[];
  configuration?: Record<string, Record<string, string>>;
  useDefaultConfig?: boolean;
}

export interface FieldConfiguration {
  fields: Record<string, Field>;
  dynamicFields: Field[];
  useDefaultConfig?: boolean;
}

export interface IndexConfiguration {
  indexSettings: IndexSettings;
  dataProcessorConfiguration: DataProcessorConfiguration;
  fieldConfiguration: FieldConfiguration;
}

type Locale = string;

export type ProductSetType = "Static" | "Dynamic" | "Generic" | "Querystring";

export type FacetType = "TERM" | "HIERARCHICAL" | "INTERVAL" | "RANGE";

export type ValueOrder =
  | "COUNT"
  | "ALPHANUM_ASC"
  | "ALPHANUM_DESC"
  | "HUMAN_NUMERIC_ASC"
  | "HUMAN_NUMERIC_DESC";

export type ScoreMode = "AVG" | "SUM" | "MAX" | "MIN" | "MULTIPLY";

export type BoostMode = "AVG" | "REPLACE" | "MULTIPLY" | "SUM";

export type ScoreType = "FIELD_VALUE_FACTOR" | "GAUSS" | "DECAY";

export type ScoreOption = string;

export type QueryBuildingSetting = string;

export type SortOrder = "ASC" | "DESC";

export interface QueryProcessingConfiguration {
  userQueryPreprocessors?: string[];
  userQueryAnalyzer?: string | null;
}

export interface FacetConfig {
  label: string;
  sourceField: string;
  type?: FacetType;
  metaData?: Record<string, any>;
  optimalValueCount?: number;
  showUnselectedOptions?: boolean;
  isMultiSelect?: boolean;
  isFilterSensitive?: boolean;
  order?: number;
  valueOrder?: ValueOrder;
  excludeFromFacetLimit?: boolean;
  preferVariantOnFilter?: boolean;
  minFacetCoverage?: number;
  minValueCount?: number;
  filterDependencies?: string[];
  removeOnSingleFullCoverageFacetElement?: boolean;
}

export interface FacetConfiguration {
  defaultTermFacetConfiguration?: FacetConfig;
  defaultNumberFacetConfiguration?: FacetConfig;
  facets?: FacetConfig[];
  maxFacets?: number;
}

export interface ScoringFunction {
  field?: string;
  type?: ScoreType;
  weight?: number;
  options?: Record<ScoreOption, string>;
}

export interface ScoringConfiguration {
  scoreMode?: ScoreMode;
  boostMode?: BoostMode;
  scoreFunctions?: ScoringFunction[];
}

export interface QueryCondition {
  minTermCount?: number;
  maxTermCount?: number;
  maxQueryLength?: number;
  matchingRegex?: string | null;
}

export interface QueryConfiguration {
  name?: string;
  condition?: QueryCondition;
  strategy?: string;
  weightedFields?: Record<string, number>;
  settings?: Record<QueryBuildingSetting, string>;
}

export interface SortOptionConfiguration {
  field?: string;
  label?: string;
  order?: SortOrder;
  missing?: string;
}

export interface ApplicationSearchProperties {
  indexName?: string;
  locale?: Locale;
  useDefaultFacetConfig?: boolean;
  useDefaultScoringConfig?: boolean;
  useDefaultQueryConfig?: boolean;
  useDefaultSortConfig?: boolean;
  variantPickingStrategy?: string;
  queryProcessing?: QueryProcessingConfiguration;
  facetConfiguration?: FacetConfiguration;
  scoringConfiguration?: ScoringConfiguration;
  queryConfiguration?: Record<string, QueryConfiguration>;
  sortConfiguration?: SortOptionConfiguration[];
  rescorers?: string[];
  customProductSetResolver?: Record<ProductSetType, string>;
  pluginConfiguration?: Record<string, Record<string, string>>;
}

export type ConfigResponse = {
  id: number;
  service: string;
  defaultConfig: Record<string, any>;
  scopedConfig: Record<string, any>;
  createdAt: string;
  isActive: boolean;
};
