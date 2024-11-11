"use client"

import { useRecoilState } from "recoil"

import { ResultHit } from "@/types/api"
import { SearchParamsMap } from "@/types/searchParams"
import { productDataFieldConfigurationState } from "@/lib/global-state"

import { ProductCard } from "./product-card"

type ProductCardsListProps = {
  hits: ResultHit[]
  query: string
  tenant: string
  productType?: "product" | "associatedProduct"
}

export default function ProductCardsList({
  hits,
  query,
  tenant,
  productType = "product",
}: ProductCardsListProps) {
  const [productDataFieldConfiguration, setProductDataFieldConfiguration] =
    useRecoilState(productDataFieldConfigurationState)

  return (
    <ul className="grid w-full grid-cols-1 gap-4 lg:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
      {hits?.map((hit) => (
        <ProductCard
          productType={productType}
          productId={hit.document?.id}
          key={hit.document?.id}
          productDataFieldConfiguration={productDataFieldConfiguration}
          url={
            `/product/${hit.document?.id}?` +
            new URLSearchParams([
              [SearchParamsMap.query, query],
              [SearchParamsMap.tenant, tenant],
            ])
          }
          hit={hit}
          heroProductSets={hit.matchedQueries
            ?.filter((matchedQuery) =>
              matchedQuery.startsWith("hero-product-set-")
            )
            .map((matchedQuery) =>
              matchedQuery.replace("hero-product-set-", "")
            )}
        />
      ))}
    </ul>
  )
}
