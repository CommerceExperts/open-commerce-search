import Image from "next/image"
import { env } from "@/env.mjs"
import { Pen } from "lucide-react"

import { components } from "@/types/productsetservice"
import { getAllProductSets } from "@/lib/productsetservice"
import { getTenants, search } from "@/lib/search-api"
import { FilterOptions } from "@/components/demoshop/filter-options"
import Pagination from "@/components/demoshop/pagination"
import SortSelect from "@/components/demoshop/sort-select"

import { Icons } from "../misc/icons"
import BookmarksButton from "./bookmarks-button"
import ClearSearchQueryButton from "./clear-search-query-button"
import ProductCardEditorButton from "./product-card-editor"
import ProductCardsList from "./product-cards-list"
import ProductDataFieldConfigurationInitialization from "./product-data-field-configuration-initialization"
import ResultsDebugDialogButton from "./results-debug-dialog"
import SmartQueryRedirect from "./smart-query-redirect"
import TenantSelectButton from "./tenant-select-button"

type SearchResultsProps = {
  tenant: string
  query: string
  searchResultsPerPage: number
  page: number
  sort: string
  filters: {
    [key: string]: string | string[] | undefined
  }
  heroProductIds: string[]
}

export default async function SearchResults({
  tenant,
  query,
  searchResultsPerPage,
  page,
  sort,
  filters,
  heroProductIds,
}: SearchResultsProps) {
  const {
    hits,
    matchCount,
    sortOptions,
    filterOptions,
    meta,
    productDataFields,
    searchResult,
  } = await search(
    tenant,
    query,
    searchResultsPerPage,
    (page - 1) * searchResultsPerPage,
    sort,
    filters,
    heroProductIds
  )

  let bookmarks: components["schemas"]["ProductSetResponseDTO"][] = []
  let showBookmarkButton = false
  if (env.PRODUCTSETSERVICE_BASEURL) {
    const { data, error } = await getAllProductSets()

    if (!error && data) {
      showBookmarkButton = true
      bookmarks = data.reverse()
    }
  }

  return (
    <section className="container grid max-w-[1600px] items-center gap-6 pb-8 pt-6 md:py-10">
      {/* @ts-ignore Async server component */}
      <ProductDataFieldConfigurationInitialization />
      <SmartQueryRedirect meta={meta ?? {}} />

      <div className="grid gap-8 lg:grid-cols-3 xl:grid-cols-4">
        <div className="flex flex-col gap-2">
          <div>
            <div className="block lg:hidden">
              <FilterOptions filterOptions={filterOptions} open={false} />
            </div>
            <div className="hidden lg:block">
              <FilterOptions filterOptions={filterOptions} open={true} />
            </div>
          </div>
        </div>

        <div className="lg:col-span-2 xl:col-span-3">
          <div className="mb-4 flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
            <p data-track-id="resultCountContainer" className="truncate">
              {query !== "" ? (
                <>
                  <span className="font-semibold">{matchCount}</span>
                  {matchCount == 1 ? " Article " : " Articles "}
                  found
                </>
              ) : (
                <>
                  <span className="font-semibold">All Products </span>(
                  {matchCount})
                </>
              )}
            </p>
            <div className="flex items-center gap-2">
              <SortSelect sortOptions={sortOptions} selectedSortOption={sort} />
              <ProductCardEditorButton
                productDataFields={productDataFields ?? []}
                className="h-10 w-10 p-1"
              >
                <Pen className="h-4 w-4" />
              </ProductCardEditorButton>
              {showBookmarkButton && (
                <BookmarksButton bookmarks={bookmarks} tenant={tenant} />
              )}
              <ResultsDebugDialogButton
                meta={meta ?? {}}
                searchResult={searchResult}
              />
            </div>
          </div>

          {hits.length > 0 ? (
            <ProductCardsList hits={hits} query={query} tenant={tenant} />
          ) : (
            <div className="flex flex-col items-center gap-4 break-all py-4">
              <Image
                src={Icons.logoUrl}
                alt="OCSS Logo"
                width={140}
                height={140}
              />
              <p className="text-lg">
                There are no search results for{" "}
                <span className="font-bold">&quot;{query}&quot;</span>.
              </p>
              <ClearSearchQueryButton tenant={tenant} />
              <p>or</p>
              <TenantSelectButton query={query} tenants={await getTenants()} />
            </div>
          )}

          <div className="my-8 flex  gap-4">
            <div className="flex w-full justify-center">
              <Pagination
                page={page}
                total={Math.ceil(matchCount / searchResultsPerPage)}
              />
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
