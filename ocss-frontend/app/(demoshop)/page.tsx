import { Suspense } from "react"
import { env } from "@/env.mjs"

import { SearchParams } from "@/types/searchParams"
import {
  extractFilters,
  extractHeroProductIds,
  extractPage,
  extractSearchParam,
  extractSort,
} from "@/lib/utils"
import Searchbar from "@/components/demoshop/search-bar"
import SearchResults from "@/components/demoshop/search-results"
import SearchResultsFallback from "@/components/demoshop/search-results-fallback"

const searchResultsPerPage = 16

type IndexPageProps = {
  searchParams: SearchParams
}

export default async function IndexPage({ searchParams }: IndexPageProps) {
  const tenant = searchParams?.tenant
    ? extractSearchParam(searchParams.tenant, 0)
    : env.DEFAULT_TENANT
  const query = extractSearchParam(searchParams?.query, 0)
  const page = extractPage(searchParams?.page)
  const sort = extractSort(searchParams?.sort)
  const filters = extractFilters(searchParams)
  const heroProductIds = extractHeroProductIds(searchParams.hpids)

  return (
    <>
      <Searchbar
        defaultValue={query}
        basepath={env.BASEPATH}
        enableSuggest={env.ENABLE_SUGGEST_API == "true"}
        tenant={tenant}
      />
      <Suspense
        key={query + page}
        fallback={
          <SearchResultsFallback searchResultsPerPage={searchResultsPerPage} />
        }
      >
        {/* @ts-expect-error Async server component */}
        <SearchResults
          tenant={tenant}
          query={query}
          searchResultsPerPage={searchResultsPerPage}
          page={page}
          sort={sort}
          filters={filters}
          heroProductIds={heroProductIds}
        />
      </Suspense>
    </>
  )
}
