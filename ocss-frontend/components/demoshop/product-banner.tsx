"use client"

import Image from "next/image"
import { ShoppingCart } from "lucide-react"
import { useRecoilState } from "recoil"

import { Product } from "@/types/api"
import { productDataFieldConfigurationState } from "@/lib/global-state"
import { cn, urlPattern } from "@/lib/utils"

import { Button } from "../ui/button"

type ProductBannerProps = {
  product: Product
}

export default function ProductBanner({ product }: ProductBannerProps) {
  const [productDataFieldConfiguration, setProductDataFieldConfiguration] =
    useRecoilState(productDataFieldConfigurationState)

  return (
    <div className="gap-12 lg:flex ">
      {productDataFieldConfiguration
        .filter((item) => item.type === "image")
        .map((item) =>
          urlPattern.test(
            (product?.data[item.sourceField] as unknown as
              | string
              | undefined) ?? ""
          ) ? (
            <div className="flex  h-[300px] w-full justify-center">
              <Image
                src={
                  (product?.data[item.sourceField] as unknown as
                    | string
                    | undefined) ?? ""
                }
                width={500}
                height={500}
                alt="Product image"
                className="mb-2 max-h-full object-contain"
              />
            </div>
          ) : (
            <p className="text-destructive">
              {item.sourceField} is not a image
            </p>
          )
        )}
      <div className="flex w-full flex-col  justify-center pt-4">
        {productDataFieldConfiguration
          .filter((item) => item.type !== "image")
          .map((item) =>
            item.type === "string" ? (
              <p
                className={cn(
                  "break-all",
                  item.style == "bold"
                    ? "text-2xl font-bold"
                    : item.style === "small"
                    ? ""
                    : "text-lg"
                )}
              >
                {(product?.data[item.sourceField] as unknown as
                  | string
                  | undefined) ?? ""}
              </p>
            ) : item.type === "price" ? (
              <p
                className={cn(
                  "break-all",
                  item.style == "bold"
                    ? "text-2xl font-bold"
                    : item.style === "small"
                    ? ""
                    : "text-lg"
                )}
              >
                {`${(
                  parseFloat(
                    (product?.data[item.sourceField] as unknown as
                      | string
                      | undefined) ?? ""
                  ) / (item.divisor ?? 1)
                ).toFixed(2)} ${item.currency ?? ""}`}
              </p>
            ) : (
              <></>
            )
          )}

        <Button
          data-track-id="addToCartPDP"
          data-product-id={product.id}
          className="mt-2 flex w-44 justify-around font-bold"
        >
          Add to cart
          <ShoppingCart className="w-4" />
        </Button>
      </div>
    </div>
  )
}
