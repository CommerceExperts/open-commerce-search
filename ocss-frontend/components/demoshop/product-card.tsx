"use client"

import Image from "next/image"
import Link from "next/link"
import { useSearchParams } from "next/navigation"
import { Crown, Info } from "lucide-react"

import { ResultHit } from "@/types/api"
import { ProductDataFieldConfiguration } from "@/types/config"
import { SearchParamsMap } from "@/types/searchParams"
import {
  cn,
  mergeURLSearchParams,
  removeQueryParamValue,
  urlPattern,
} from "@/lib/utils"
import { Card, CardContent } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTrigger,
} from "@/components/ui/dialog"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"

import { Icons } from "../misc/icons"
import { buttonVariants } from "../ui/button"
import { Textarea } from "../ui/textarea"

type ProductCardProps = {
  url: string
  hit: ResultHit
  productId?: string
  productType: "product" | "associatedProduct"
  productDataFieldConfiguration?: ProductDataFieldConfiguration[]
  heroProductSets?: string[]
}

export function ProductCard({
  url,
  productId,
  productType,
  hit,
  productDataFieldConfiguration,
  heroProductSets,
}: ProductCardProps) {
  const searchParams = useSearchParams()
  const isHeroProduct = !!heroProductSets && heroProductSets?.length > 0

  return (
    <>
      <Card
        data-track-id={productType}
        data-product-id={productId}
        className={cn(isHeroProduct ? "border-yellow-400" : "")}
      >
        <CardContent className="mt-4">
          <Link href={url}>
            {productDataFieldConfiguration?.map((item) => (
              <>
                {item.type === "image" ? (
                  urlPattern.test(
                    (hit.document?.data[item.sourceField] as unknown as
                      | string
                      | undefined) ?? ""
                  ) ? (
                    <div className="my-4 h-[220px]">
                      <Image
                        src={
                          (hit.document?.data[item.sourceField] as unknown as
                            | string
                            | undefined) ?? ""
                        }
                        width={1000}
                        height={1000}
                        alt="Product image"
                        className="mb-2 max-h-full object-contain"
                      />
                    </div>
                  ) : (
                    <p className="text-destructive">
                      {item.sourceField} is not a image
                    </p>
                  )
                ) : item.type === "string" ? (
                  <p
                    className={cn(
                      "truncate",
                      item.style == "bold"
                        ? "font-bold"
                        : item.style === "small"
                        ? "text-sm"
                        : ""
                    )}
                  >
                    {(hit.document?.data[item.sourceField] as unknown as
                      | string
                      | undefined) ?? ""}
                  </p>
                ) : (
                  <p
                    className={cn(
                      "truncate",
                      item.style == "bold"
                        ? "font-bold"
                        : item.style === "small"
                        ? "text-sm"
                        : ""
                    )}
                  >
                    {`${(
                      parseFloat(
                        (hit.document?.data[item.sourceField] as unknown as
                          | string
                          | undefined) ?? ""
                      ) / (item.divisor ?? 1)
                    ).toFixed(2)} ${item.currency ?? ""}`}
                  </p>
                )}
              </>
            ))}
          </Link>

          <div className="mt-2 flex items-center justify-between gap-2">
            <Link
              href={url}
              className={cn(
                buttonVariants(),
                "flex h-9 w-full gap-2 rounded-full font-bold",
                isHeroProduct
                  ? "bg-gradient-to-r from-yellow-300 via-yellow-400 to-yellow-500 hover:from-yellow-200 hover:via-yellow-300 hover:to-yellow-400"
                  : ""
              )}
            >
              DETAILS
              <Icons.arrowRight className="w-4" />
            </Link>
            <Link
              href={
                "/?" +
                (productId && isHeroProduct
                  ? removeQueryParamValue(
                      new URLSearchParams(searchParams),
                      SearchParamsMap.heroProductIds,
                      productId
                    )
                  : mergeURLSearchParams(
                      new URLSearchParams(searchParams),
                      new URLSearchParams([
                        [SearchParamsMap.heroProductIds, productId ?? ""],
                      ])
                    ))
              }
              className={cn(
                buttonVariants(),
                "flex h-9 w-9 gap-2 rounded-full p-2",
                isHeroProduct
                  ? "bg-gradient-to-r from-yellow-300 via-yellow-400 to-yellow-500 hover:from-yellow-200 hover:via-yellow-300 hover:to-yellow-400"
                  : ""
              )}
            >
              <Crown className="h-5 w-5" />
            </Link>

            <Dialog>
              <DialogTrigger>
                <div
                  className={cn(
                    buttonVariants(),
                    "flex h-9 w-9 gap-2 rounded-full p-2",
                    isHeroProduct
                      ? "bg-gradient-to-r from-yellow-300 via-yellow-400 to-yellow-500 hover:from-yellow-200 hover:via-yellow-300 hover:to-yellow-400"
                      : ""
                  )}
                >
                  <Info className="h-5 w-5" />
                </div>
              </DialogTrigger>
              <DialogContent className="max-h-screen overflow-y-scroll lg:max-w-screen-sm">
                <DialogHeader>
                  <DialogDescription>
                    {hit.metaData && (
                      <>
                        <h3 className="text-lg font-bold text-primary">
                          Metadata
                        </h3>

                        <Table>
                          <TableHeader>
                            <TableRow>
                              <TableHead className="w-[100px]">Field</TableHead>
                              <TableHead>Data</TableHead>
                            </TableRow>
                          </TableHeader>
                          <TableBody>
                            {Object.keys(hit.metaData).map((key) => (
                              <TableRow>
                                <TableCell className="font-medium">
                                  {key}
                                </TableCell>
                                <TableCell className="break-all">
                                  {JSON.stringify(hit.metaData[key] as any)}
                                </TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </>
                    )}
                    {hit.matchedQueries && (
                      <>
                        <h3 className="text-lg font-bold text-primary">
                          Matched queries
                        </h3>

                        <ul className="ml-4 list-disc break-all">
                          {hit.matchedQueries.map((matchQuery) => (
                            <li>{matchQuery}</li>
                          ))}
                        </ul>
                      </>
                    )}

                    <h3 className="mt-4 text-lg font-bold text-primary">
                      Raw response
                    </h3>
                    <Textarea className="h-[250px]" readOnly>
                      {JSON.stringify(hit, null, 2)}
                    </Textarea>
                  </DialogDescription>
                </DialogHeader>
              </DialogContent>
            </Dialog>
          </div>
        </CardContent>
      </Card>
    </>
  )
}
