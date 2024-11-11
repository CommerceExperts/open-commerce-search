export type SearchParam = string | string[] | undefined

export enum SearchParamsMap {
  tenant = "tenant",
  query = "query",
  sort = "sort",
  page = "page",

  config = "config",
  commit = "commit",
  heroProductIds = "hpids",
}

export type SearchParams = {
  [SearchParamsMap.tenant]: SearchParam
  [SearchParamsMap.query]: SearchParam
  [SearchParamsMap.sort]: SearchParam
  [SearchParamsMap.page]: SearchParam
  [SearchParamsMap.config]: SearchParam
  [SearchParamsMap.commit]: SearchParam
  [SearchParamsMap.commit]: SearchParam
  [SearchParamsMap.heroProductIds]: SearchParam
  [key: string]: string | string[] | undefined
}
