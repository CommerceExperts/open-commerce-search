import { env } from "@/env.mjs"

import type {
  ArrangedSearchQuery,
  Facet,
  Product,
  ResultHit,
  SearchResult,
  Sorting,
} from "@/types/api"

export async function getTenants() {
  if (!env.SEARCH_API_URL) {
    return []
  }

  const res = await fetch(`${env.SEARCH_API_URL}/search-api/v1/tenants`, {
    headers: {
      Authorization: `Basic ${env.SEARCH_API_AUTH}`,
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    cache: "no-store",
  })
  const data = (await res.json()) as string[]

  return data
}

export const defaultSortOption = {
  label: "Relevance",
  field: "relevance",
}

export async function search(
  tenant: string,
  query: string,
  limit: number,
  offset: number,
  sort: string,
  filters: { [key: string]: string | string[] | undefined },
  heroProductIds?: string[]
) {
  let hits: ResultHit[] = []
  let matchCount = 0
  let sortOptions: Sorting[] = []
  let filterOptions: Facet[] = []
  let productDataFields: string[] = []
  let meta:
    | {
        [key: string]: object
      }
    | undefined

  if (!env.SEARCH_API_URL) {
    return { hits, matchCount, sortOptions, filterOptions }
  }

  const _filters = { ...filters }
  Object.keys(_filters).forEach((key) => {
    const value = _filters[key]
    if (value) {
      _filters[key] = Array.isArray(value) ? value.join(",") : _filters[key]
    }
  }, {})

  const res = await fetch(
    `${env.SEARCH_API_URL}/search-api/v1/search/arranged/${tenant}`,
    {
      method: "post",
      headers: {
        Authorization: `Basic ${env.SEARCH_API_AUTH}`,
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        q: query,
        limit,
        offset,
        filters: _filters,
        sort: sort ? sort : undefined,
        arrangedProductSets:
          heroProductIds && heroProductIds.length > 0
            ? [{ type: "static", name: "hero-products", ids: heroProductIds }]
            : undefined,
      } as ArrangedSearchQuery),
      cache: "no-store",
    }
  )
  const searchResult = (await res.json()) as SearchResult

  // Extracting meta data
  meta = searchResult.meta

  // Extracting products
  searchResult.slices?.forEach((slice) => {
    if (slice.hits) {
      hits = hits.concat(slice.hits)
    }
  })

  if (searchResult) {
    // Extracting matchcount
    searchResult.slices?.forEach((slice) => {
      matchCount += slice.matchCount ?? 0
    })

    // Extracting sort options
    sortOptions = [
      defaultSortOption,
      ...(searchResult.sortOptions?.map((sortOption) => ({
        ...sortOption,
        field:
          sortOption.sortOrder?.toLowerCase() === "asc"
            ? sortOption.field
            : `-${sortOption.field}`,
      })) || ([] as Sorting[])),
    ]

    // Extracting filter options
    filterOptions = searchResult.slices?.[0]?.facets || ([] as Facet[])

    // Extracting product data fields
    productDataFields = Object.keys(
      searchResult.slices?.[0].hits?.[0]?.document?.data ?? {}
    )
  }

  return {
    hits,
    matchCount,
    sortOptions,
    filterOptions,
    meta,
    productDataFields,
    searchResult,
  }
}

export async function getProduct(tenant: string, id: string) {
  if (!env.SEARCH_API_URL) {
    return
  }

  const res = await fetch(
    `${env.SEARCH_API_URL}/search-api/v1/doc/${tenant}/${id}`,
    {
      headers: {
        Authorization: `Basic ${env.SEARCH_API_AUTH}`,
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      cache: "no-store",
    }
  )
  const data = (await res.json()) as Product

  if (!data.id) {
    return
  }

  return data
}
