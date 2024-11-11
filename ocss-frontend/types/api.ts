/**
 *
 * @export
 * @interface ArrangedSearchQuery
 */
export interface ArrangedSearchQuery {
  /**
   *
   * @type {Array<ProductSet>}
   * @memberof ArrangedSearchQuery
   */
  arrangedProductSets?: Array<ProductSet | StaticProductSet | DynamicProductSet>
  /**
   *
   * @type {{ [key: string]: string; }}
   * @memberof ArrangedSearchQuery
   */
  filters?: { [key: string]: string }
  /**
   *
   * @type {boolean}
   * @memberof ArrangedSearchQuery
   */
  includeMainResult?: boolean
  /**
   * The amount of products to return in the result
   * @type {number}
   * @memberof ArrangedSearchQuery
   */
  limit?: number
  /**
   * The amount of products to omit from the whole result to select the returned results.
   * @type {number}
   * @memberof ArrangedSearchQuery
   */
  offset?: number
  /**
   * the user query
   * @type {string}
   * @memberof ArrangedSearchQuery
   */
  q?: string
  /**
   * Full sorting parameter value. This is the name of the sorting and optionally a dash as prefix, thats means the sorting should be descending. Several sorting criterion can be defined by separating the values using comma.
   * @type {string}
   * @memberof ArrangedSearchQuery
   */
  sort?: string
  /**
   * flag to specify if facets should be returned with the requested response. Should be set to false in case only the next batch of hits is requested (e.g. for endless scrolling).
   * @type {boolean}
   * @memberof ArrangedSearchQuery
   */
  withFacets?: boolean
}
/**
 * Rich model that can be used to represent a document\'s or product\'s attribute. The attribute \'name\' should be a URL friendly identifier for that attribute (rather maxSpeed than \'Max Speed\'). It will be used as filter parameter laster. If the attribute \'code\' is provieded, it can be used for consistent filtering, even if the value name should change. The values are used to produce nice facets or if used for search, they will be added to the searchable content.
 * @export
 * @interface Attribute
 */
export interface Attribute {
  /**
   * Optional: code is considered as ID of the attribute value, e.g. \"FF0000\" for color
   * @type {string}
   * @memberof Attribute
   */
  code?: string
  /**
   * The name SHOULD be URL friendly identifier for the attribute, since it could be used to build according filter parameters.
   * @type {string}
   * @memberof Attribute
   */
  name: string
  /**
   * Human readable representation of that attribute, e.g. \'Red\' for the attribute \'Color\'
   * @type {string}
   * @memberof Attribute
   */
  value: string
}
/**
 * composite object that is used to add documents to the index.
 * @export
 * @interface BulkimportData
 */
export interface BulkimportData {
  /**
   *
   * @type {Array<Document>}
   * @memberof BulkimportData
   */
  documents: Array<Document>
  /**
   *
   * @type {importSession}
   * @memberof BulkimportData
   */
  session: importSession
}
/**
 * categories are treated in a parent-child relationship, so a product can be placed into a path within a category tree. Multiple category paths can be defined per document.
 * @export
 * @interface Category
 */
export interface Category {
  /**
   * Optional ID for a consistent filtering
   * @type {string}
   * @memberof Category
   */
  id?: string
  /**
   *
   * @type {string}
   * @memberof Category
   */
  name: string
}
/**
 * A data record that contains any data relevant for search. The single field types and conversions are part of the according service configuration.
 * @export
 * @interface Document
 */
export interface Document {
  /**
   * multiple attributes can be delivered separately from standard data fields
   * @type {Array<Attribute>}
   * @memberof Document
   */
  attributes?: Array<Attribute>
  /**
   *
   * @type {Array<Array<Category>>}
   * @memberof Document
   */
  categories?: Array<Array<Category>>
  /**
   * The data property should be used for standard fields, such as title, description, price. Only values of the following types are accepted (others will be dropped silently): Standard primitive types (Boolean, String, Integer, Double) and arrays of these types. Attributes (key-value objects with ID) should be passed to the attributes property.
   * @type {{ [key: string]: object; }}
   * @memberof Document
   */
  data: { [key: string]: object }
  /**
   *
   * @type {string}
   * @memberof Document
   */
  id: string
}
/**
 *
 * @export
 * @interface DynamicProductSet
 */
export interface DynamicProductSet extends ProductSet {
  /**
   *
   * @type {boolean}
   * @memberof DynamicProductSet
   */
  asSeparateSlice?: boolean
  /**
   *
   * @type {{ [key: string]: string; }}
   * @memberof DynamicProductSet
   */
  filters?: { [key: string]: string }
  /**
   *
   * @type {number}
   * @memberof DynamicProductSet
   */
  limit?: number
  /**
   *
   * @type {string}
   * @memberof DynamicProductSet
   */
  name?: string
  /**
   *
   * @type {string}
   * @memberof DynamicProductSet
   */
  query?: string
  /**
   *
   * @type {number}
   * @memberof DynamicProductSet
   */
  size?: number
  /**
   *
   * @type {string}
   * @memberof DynamicProductSet
   */
  sort?: string
  /**
   *
   * @type {string}
   * @memberof DynamicProductSet
   */
  type?: string
}
/**
 *
 * @export
 * @interface DynamicProductSetAllOf
 */
export interface DynamicProductSetAllOf {
  /**
   *
   * @type {boolean}
   * @memberof DynamicProductSetAllOf
   */
  asSeparateSlice?: boolean
  /**
   *
   * @type {{ [key: string]: string; }}
   * @memberof DynamicProductSetAllOf
   */
  filters?: { [key: string]: string }
  /**
   *
   * @type {number}
   * @memberof DynamicProductSetAllOf
   */
  limit?: number
  /**
   *
   * @type {string}
   * @memberof DynamicProductSetAllOf
   */
  name?: string
  /**
   *
   * @type {string}
   * @memberof DynamicProductSetAllOf
   */
  query?: string
  /**
   *
   * @type {number}
   * @memberof DynamicProductSetAllOf
   */
  size?: number
  /**
   *
   * @type {string}
   * @memberof DynamicProductSetAllOf
   */
  sort?: string
  /**
   *
   * @type {string}
   * @memberof DynamicProductSetAllOf
   */
  type?: string
}
/**
 * If facets are part of this slice, they are placed here. By default only one slice SHOULD contain facets.
 * @export
 * @interface Facet
 */
export interface Facet {
  /**
   * This is the amount of matched documents that are covered by that facet.
   * @type {number}
   * @memberof Facet
   */
  absoluteFacetCoverage?: number
  /**
   * The entries of that facet.
   * @type {Array<FacetEntry>}
   * @memberof Facet
   */
  entries?: Array<FacetEntry>
  /**
   * This is the name coming from the data. Separate label information should be available in the meta data.
   * @type {string}
   * @memberof Facet
   */
  fieldName?: string
  /**
   *
   * @type {boolean}
   * @memberof Facet
   */
  filtered?: boolean
  /**
   * Is set to true if there an active filter from that facet.
   * @type {boolean}
   * @memberof Facet
   */
  isFiltered?: boolean
  /**
   * Optional meta data for that facet, e.g. display hints like a label or a facet-type.
   * @type {{ [key: string]: object; }}
   * @memberof Facet
   */
  meta?: { [key: string]: object }
  /**
   * The type of the facet, so the kind of FacetEntries it contains. See the according FacetEntry variants for more details.
   * @type {string}
   * @memberof Facet
   */
  type?: FacetTypeEnum
}

export const FacetTypeEnum = {
  Term: "term",
  Hierarchical: "hierarchical",
  Interval: "interval",
  Range: "range",
} as const

export type FacetTypeEnum = (typeof FacetTypeEnum)[keyof typeof FacetTypeEnum]

/**
 * The entries of that facet.
 * @export
 * @interface FacetEntry
 */
export interface FacetEntry {
  /**
   * Estimated amount of documents that will be returned, if this facet entry is picked as filter.
   * @type {number}
   * @memberof FacetEntry
   */
  docCount?: number
  /**
   *
   * @type {string}
   * @memberof FacetEntry
   */
  id?: string
  /**
   *
   * @type {string}
   * @memberof FacetEntry
   */
  key?: string
  /**
   *
   * @type {string}
   * @memberof FacetEntry
   */
  link?: string
  /**
   * Should be set to true in the response, if that filter is actually selected.
   * @type {boolean}
   * @memberof FacetEntry
   */
  selected?: boolean
  /**
   *
   * @type {string}
   * @memberof FacetEntry
   */
  type?: string
  /**
   *
   * @type {number}
   * @memberof FacetEntry
   */
  lowerBound?: number
  /**
   *
   * @type {number}
   * @memberof FacetEntry
   */
  upperBound?: number
}
/**
 *
 * @export
 * @interface HierarchialFacetEntry
 */
export interface HierarchialFacetEntry extends FacetEntry {
  /**
   * Child facet entries to that particular facet. The child facets again could be HierarchialFacetEntries.
   * @type {Array<FacetEntry>}
   * @memberof HierarchialFacetEntry
   */
  children?: Array<FacetEntry>
  /**
   * Estimated amount of documents that will be returned, if this facet entry is picked as filter.
   * @type {number}
   * @memberof HierarchialFacetEntry
   */
  docCount?: number
  /**
   *
   * @type {string}
   * @memberof HierarchialFacetEntry
   */
  id?: string
  /**
   *
   * @type {string}
   * @memberof HierarchialFacetEntry
   */
  key?: string
  /**
   *
   * @type {string}
   * @memberof HierarchialFacetEntry
   */
  link?: string
  /**
   *
   * @type {string}
   * @memberof HierarchialFacetEntry
   */
  path?: string
  /**
   * Should be set to true in the response, if that filter is actually selected.
   * @type {boolean}
   * @memberof HierarchialFacetEntry
   */
  selected?: boolean
  /**
   *
   * @type {string}
   * @memberof HierarchialFacetEntry
   */
  type?: string
}
/**
 *
 * @export
 * @interface HierarchialFacetEntryAllOf
 */
export interface HierarchialFacetEntryAllOf {
  /**
   * Child facet entries to that particular facet. The child facets again could be HierarchialFacetEntries.
   * @type {Array<FacetEntry>}
   * @memberof HierarchialFacetEntryAllOf
   */
  children?: Array<FacetEntry>
  /**
   * Estimated amount of documents that will be returned, if this facet entry is picked as filter.
   * @type {number}
   * @memberof HierarchialFacetEntryAllOf
   */
  docCount?: number
  /**
   *
   * @type {string}
   * @memberof HierarchialFacetEntryAllOf
   */
  id?: string
  /**
   *
   * @type {string}
   * @memberof HierarchialFacetEntryAllOf
   */
  key?: string
  /**
   *
   * @type {string}
   * @memberof HierarchialFacetEntryAllOf
   */
  link?: string
  /**
   *
   * @type {string}
   * @memberof HierarchialFacetEntryAllOf
   */
  path?: string
  /**
   * Should be set to true in the response, if that filter is actually selected.
   * @type {boolean}
   * @memberof HierarchialFacetEntryAllOf
   */
  selected?: boolean
  /**
   *
   * @type {string}
   * @memberof HierarchialFacetEntryAllOf
   */
  type?: string
}
/**
 *
 * @export
 * @interface importSession
 */
export interface importSession {
  /**
   *
   * @type {string}
   * @memberof importSession
   */
  finalIndexName: string
  /**
   *
   * @type {string}
   * @memberof importSession
   */
  temporaryIndexName: string
}
/**
 * Facet entry that describes a numerical interval. If only the lower value or only the upper value is set, this means it\'s an open ended interval, e.g. \'< 100\' for upper bound only.
 * @export
 * @interface IntervalFacetEntry
 */
export interface IntervalFacetEntry extends FacetEntry {
  /**
   * Estimated amount of documents that will be returned, if this facet entry is picked as filter.
   * @type {number}
   * @memberof IntervalFacetEntry
   */
  docCount?: number
  /**
   *
   * @type {string}
   * @memberof IntervalFacetEntry
   */
  id?: string
  /**
   *
   * @type {string}
   * @memberof IntervalFacetEntry
   */
  key?: string
  /**
   *
   * @type {string}
   * @memberof IntervalFacetEntry
   */
  link?: string
  /**
   *
   * @type {number}
   * @memberof IntervalFacetEntry
   */
  lowerBound?: number
  /**
   * Should be set to true in the response, if that filter is actually selected.
   * @type {boolean}
   * @memberof IntervalFacetEntry
   */
  selected?: boolean
  /**
   *
   * @type {string}
   * @memberof IntervalFacetEntry
   */
  type?: string
  /**
   *
   * @type {number}
   * @memberof IntervalFacetEntry
   */
  upperBound?: number
}
/**
 *
 * @export
 * @interface IntervalFacetEntryAllOf
 */
export interface IntervalFacetEntryAllOf {
  /**
   * Estimated amount of documents that will be returned, if this facet entry is picked as filter.
   * @type {number}
   * @memberof IntervalFacetEntryAllOf
   */
  docCount?: number
  /**
   *
   * @type {string}
   * @memberof IntervalFacetEntryAllOf
   */
  id?: string
  /**
   *
   * @type {string}
   * @memberof IntervalFacetEntryAllOf
   */
  key?: string
  /**
   *
   * @type {string}
   * @memberof IntervalFacetEntryAllOf
   */
  link?: string
  /**
   *
   * @type {number}
   * @memberof IntervalFacetEntryAllOf
   */
  lowerBound?: number
  /**
   * Should be set to true in the response, if that filter is actually selected.
   * @type {boolean}
   * @memberof IntervalFacetEntryAllOf
   */
  selected?: boolean
  /**
   *
   * @type {string}
   * @memberof IntervalFacetEntryAllOf
   */
  type?: string
  /**
   *
   * @type {number}
   * @memberof IntervalFacetEntryAllOf
   */
  upperBound?: number
}
/**
 * Main product containing the data that is common for all variants. A product may represent a master-variant relation ship. A variant should be associated to a single Product and cannot have variants again - those will be ignored. It should only contain data special to that variant. Data that is common to all variants should be set at master level.
 * @export
 * @interface Product
 */
export interface Product {
  /**
   * multiple attributes can be delivered separately from standard data fields
   * @type {Array<Attribute>}
   * @memberof Product
   */
  attributes?: Array<Attribute>
  /**
   *
   * @type {Array<Array<Category>>}
   * @memberof Product
   */
  categories?: Array<Array<Category>>
  /**
   * The data property should be used for standard fields, such as title, description, price. Only values of the following types are accepted (others will be dropped silently): Standard primitive types (Boolean, String, Integer, Double) and arrays of these types. Attributes (key-value objects with ID) should be passed to the attributes property.
   * @type {{ [key: string]: object; }}
   * @memberof Product
   */
  data: { [key: string]: object }
  /**
   *
   * @type {string}
   * @memberof Product
   */
  id: string
  /**
   * for products without variants, it can be null or rather us a document directly.
   * @type {Array<Document>}
   * @memberof Product
   */
  variants?: Array<Document>
}
/**
 *
 * @export
 * @interface ProductAllOf
 */
export interface ProductAllOf {
  /**
   * for products without variants, it can be null or rather us a document directly.
   * @type {Array<Document>}
   * @memberof ProductAllOf
   */
  variants?: Array<Document>
}
/**
 *
 * @export
 * @interface ProductSet
 */
export interface ProductSet {
  /**
   *
   * @type {boolean}
   * @memberof ProductSet
   */
  asSeparateSlice?: boolean
  /**
   *
   * @type {string}
   * @memberof ProductSet
   */
  name?: string
  /**
   *
   * @type {number}
   * @memberof ProductSet
   */
  size?: number
  /**
   *
   * @type {string}
   * @memberof ProductSet
   */
  type?: string
}
/**
 * Facet entry that describes the complete range of the facet. If a filter is picked, the selectedMin and selectedMax value are set, otherwise null.
 * @export
 * @interface RangeFacetEntry
 */
export interface RangeFacetEntry extends FacetEntry {
  /**
   * Estimated amount of documents that will be returned, if this facet entry is picked as filter.
   * @type {number}
   * @memberof RangeFacetEntry
   */
  docCount?: number
  /**
   *
   * @type {string}
   * @memberof RangeFacetEntry
   */
  id?: string
  /**
   *
   * @type {string}
   * @memberof RangeFacetEntry
   */
  key?: string
  /**
   *
   * @type {string}
   * @memberof RangeFacetEntry
   */
  link?: string
  /**
   *
   * @type {number}
   * @memberof RangeFacetEntry
   */
  lowerBound?: number
  /**
   * Should be set to true in the response, if that filter is actually selected.
   * @type {boolean}
   * @memberof RangeFacetEntry
   */
  selected?: boolean
  /**
   *
   * @type {number}
   * @memberof RangeFacetEntry
   */
  selectedMax?: number
  /**
   *
   * @type {number}
   * @memberof RangeFacetEntry
   */
  selectedMin?: number
  /**
   *
   * @type {string}
   * @memberof RangeFacetEntry
   */
  type?: string
  /**
   *
   * @type {number}
   * @memberof RangeFacetEntry
   */
  upperBound?: number
}
/**
 *
 * @export
 * @interface RangeFacetEntryAllOf
 */
export interface RangeFacetEntryAllOf {
  /**
   * Estimated amount of documents that will be returned, if this facet entry is picked as filter.
   * @type {number}
   * @memberof RangeFacetEntryAllOf
   */
  docCount?: number
  /**
   *
   * @type {string}
   * @memberof RangeFacetEntryAllOf
   */
  id?: string
  /**
   *
   * @type {string}
   * @memberof RangeFacetEntryAllOf
   */
  key?: string
  /**
   *
   * @type {string}
   * @memberof RangeFacetEntryAllOf
   */
  link?: string
  /**
   *
   * @type {number}
   * @memberof RangeFacetEntryAllOf
   */
  lowerBound?: number
  /**
   * Should be set to true in the response, if that filter is actually selected.
   * @type {boolean}
   * @memberof RangeFacetEntryAllOf
   */
  selected?: boolean
  /**
   *
   * @type {number}
   * @memberof RangeFacetEntryAllOf
   */
  selectedMax?: number
  /**
   *
   * @type {number}
   * @memberof RangeFacetEntryAllOf
   */
  selectedMin?: number
  /**
   *
   * @type {string}
   * @memberof RangeFacetEntryAllOf
   */
  type?: string
  /**
   *
   * @type {number}
   * @memberof RangeFacetEntryAllOf
   */
  upperBound?: number
}
/**
 * the list of actual hits for that result view.
 * @export
 * @interface ResultHit
 */
export interface ResultHit {
  /**
   *
   * @type {Document}
   * @memberof ResultHit
   */
  document?: Document
  /**
   *
   * @type {string}
   * @memberof ResultHit
   */
  index?: string
  /**
   *
   * @type {Array<string>}
   * @memberof ResultHit
   */
  matchedQueries?: Array<string>
  metaData: { [key: string]: unknown }
}
/**
 *
 * @export
 * @interface SearchQuery
 */
export interface SearchQuery {
  /**
   * The amount of products to return in the result
   * @type {number}
   * @memberof SearchQuery
   */
  limit?: number
  /**
   * The amount of products to omit from the whole result to select the returned results.
   * @type {number}
   * @memberof SearchQuery
   */
  offset?: number
  /**
   * the user query
   * @type {string}
   * @memberof SearchQuery
   */
  q?: string
  /**
   * Full sorting parameter value. This is the name of the sorting and optionally a dash as prefix, thats means the sorting should be descending. Several sorting criterion can be defined by separating the values using comma.
   * @type {string}
   * @memberof SearchQuery
   */
  sort?: string
  /**
   * flag to specify if facets should be returned with the requested response. Should be set to false in case only the next batch of hits is requested (e.g. for endless scrolling).
   * @type {boolean}
   * @memberof SearchQuery
   */
  withFacets?: boolean
}
/**
 *
 * @export
 * @interface SearchResult
 */
export interface SearchResult {
  /**
   * The URI that was used to get that result view. May be used to generate breadcrumbs.
   * @type {string}
   * @memberof SearchResult
   */
  inputURI?: string
  /**
   *
   * @type {{ [key: string]: object; }}
   * @memberof SearchResult
   */
  meta?: { [key: string]: object }
  /**
   * The result may consist of several slices, for example if a search request couldn\'t be answered matching all words (e.g. \"striped nike shirt\") then one slice could be the result for one part of the query (e.g. \"striped shirt\") and the other could be for another part of the query (e.g. \"nike shirt\"). This can also be used to deliver some special advertised products or to split the result in different ranked slices (e.g. the first 3 results are ranked by popularity, the next 3 are sorted by price and the rest is ranked by \'default\' relevance). Each slice contains the {@link SearchQuery} that represent that exact slice. At least 1 slice should be expected. If there is no slice, no results were found.
   * @type {Array<SearchResultSlice>}
   * @memberof SearchResult
   */
  slices?: Array<SearchResultSlice>
  /**
   *
   * @type {Array<Sorting>}
   * @memberof SearchResult
   */
  sortOptions?: Array<Sorting>
  /**
   * amount of time the internal search needed to compute that result
   * @type {number}
   * @memberof SearchResult
   */
  tookInMillis?: number
}
/**
 * The result may consist of several slices, for example if a search request couldn\'t be answered matching all words (e.g. \"striped nike shirt\") then one slice could be the result for one part of the query (e.g. \"striped shirt\") and the other could be for another part of the query (e.g. \"nike shirt\"). This can also be used to deliver some special advertised products or to split the result in different ranked slices (e.g. the first 3 results are ranked by popularity, the next 3 are sorted by price and the rest is ranked by \'default\' relevance). Each slice contains the {@link SearchQuery} that represent that exact slice. At least 1 slice should be expected. If there is no slice, no results were found.
 * @export
 * @interface SearchResultSlice
 */
export interface SearchResultSlice {
  /**
   * If facets are part of this slice, they are placed here. By default only one slice SHOULD contain facets.
   * @type {Array<Facet>}
   * @memberof SearchResultSlice
   */
  facets?: Array<Facet>
  /**
   * the list of actual hits for that result view.
   * @type {Array<ResultHit>}
   * @memberof SearchResultSlice
   */
  hits?: Array<ResultHit>
  /**
   * An identifier for that result slice. Can be used to differentiate different slices. Values depend on the implementation.
   * @type {string}
   * @memberof SearchResultSlice
   */
  label?: string
  /**
   * the absolute number of matches in this result.
   * @type {number}
   * @memberof SearchResultSlice
   */
  matchCount?: number
  /**
   * URL conform query parameters, that has to be used to get the next bunch of results. Is null if there are no more results.
   * @type {string}
   * @memberof SearchResultSlice
   */
  nextLink?: string
  /**
   * the offset value to use to get the next result batch
   * @type {number}
   * @memberof SearchResultSlice
   */
  nextOffset?: number
  /**
   * The query that represents exact that passed slice. If send to the engine again, that slice should be returned as main result.
   * @type {string}
   * @memberof SearchResultSlice
   */
  resultLink?: string
}
/**
 *
 * @export
 * @interface Sorting
 */
export interface Sorting {
  /**
   *
   * @type {boolean}
   * @memberof Sorting
   */
  active?: boolean
  /**
   *
   * @type {string}
   * @memberof Sorting
   */
  field?: string
  /**
   *
   * @type {boolean}
   * @memberof Sorting
   */
  isActive?: boolean
  /**
   *
   * @type {string}
   * @memberof Sorting
   */
  label?: string
  /**
   *
   * @type {string}
   * @memberof Sorting
   */
  link?: string
  /**
   *
   * @type {string}
   * @memberof Sorting
   */
  sortOrder?: SortingSortOrderEnum
}

export const SortingSortOrderEnum = {
  Asc: "asc",
  Desc: "desc",
} as const

export type SortingSortOrderEnum =
  (typeof SortingSortOrderEnum)[keyof typeof SortingSortOrderEnum]

/**
 *
 * @export
 * @interface StaticProductSet
 */
export interface StaticProductSet extends ProductSet {
  /**
   *
   * @type {boolean}
   * @memberof StaticProductSet
   */
  asSeparateSlice?: boolean
  /**
   *
   * @type {Array<string>}
   * @memberof StaticProductSet
   */
  ids?: Array<string>
  /**
   *
   * @type {string}
   * @memberof StaticProductSet
   */
  name?: string
  /**
   *
   * @type {number}
   * @memberof StaticProductSet
   */
  size?: number
  /**
   *
   * @type {string}
   * @memberof StaticProductSet
   */
  type?: string
}
/**
 *
 * @export
 * @interface StaticProductSetAllOf
 */
export interface StaticProductSetAllOf {
  /**
   *
   * @type {boolean}
   * @memberof StaticProductSetAllOf
   */
  asSeparateSlice?: boolean
  /**
   *
   * @type {Array<string>}
   * @memberof StaticProductSetAllOf
   */
  ids?: Array<string>
  /**
   *
   * @type {string}
   * @memberof StaticProductSetAllOf
   */
  name?: string
  /**
   *
   * @type {number}
   * @memberof StaticProductSetAllOf
   */
  size?: number
  /**
   *
   * @type {string}
   * @memberof StaticProductSetAllOf
   */
  type?: string
}
/**
 *
 * @export
 * @interface Suggestion
 */
export interface Suggestion {
  /**
   * arbitrary payload attached to that suggestion. Default: null
   * @type {{ [key: string]: string; }}
   * @memberof Suggestion
   */
  payload?: { [key: string]: string }
  /**
   * The phrase that is suggested and/or used as suggestion label.
   * @type {string}
   * @memberof Suggestion
   */
  phrase?: string
  /**
   * Optional type of that suggestion. Should be different for the different kind of suggested data. Default: \'keyword\'
   * @type {string}
   * @memberof Suggestion
   */
  type?: string
}
