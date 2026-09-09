import { notFound } from "next/navigation"
import { env } from "@/env.mjs"

import { SearchParams, SearchParamsMap } from "@/types/searchParams"
import { getProduct, search } from "@/lib/search-api"
import { extractSearchParam } from "@/lib/utils"
import ProductBanner from "@/components/demoshop/product-banner"
import ProductCardsList from "@/components/demoshop/product-cards-list"
import ProductDataFieldConfigurationInitialization from "@/components/demoshop/product-data-field-configuration-initialization"
import ProductDataTables from "@/components/demoshop/product-data-tables"
import Searchbar from "@/components/demoshop/search-bar"

type ProductPageProps = {
  params: Promise<{
    id: string
  }>
  searchParams: Promise<
    Pick<SearchParams, SearchParamsMap.tenant | SearchParamsMap.query>
  >
}

export default async function ProductPage(props: ProductPageProps) {
  const searchParams = await props.searchParams
  const params = await props.params
  const tenant = extractSearchParam(searchParams?.tenant, 0)
  const product = await getProduct(tenant, params.id)
  const query = extractSearchParam(searchParams?.query, 0)
  const { hits: similiarHits } = await search(tenant, query, 3, 0, "", {})

  if (!product) {
    notFound()
  }

  return (
    <>
      <ProductDataFieldConfigurationInitialization />

      <Searchbar
        enableSuggest={env.ENABLE_SUGGEST_API == "true"}
        defaultValue={query}
        basepath={env.BASEPATH}
        tenant={tenant}
      />

      <section className="container grid grid-cols-1 items-center justify-center gap-6 pb-8 pt-6 md:py-10">
        <ProductBanner product={product} />

        <ProductDataTables product={product} />

        <div className="w-ful">
          <h2 className="mb-2 text-2xl font-bold">Similiar Products</h2>
          <ProductCardsList
            hits={similiarHits}
            query={query}
            tenant={tenant}
            productType="associatedProduct"
          />
        </div>
      </section>
    </>
  )
}
