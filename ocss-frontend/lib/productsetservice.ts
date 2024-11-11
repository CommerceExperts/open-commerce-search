"use server"

import { revalidateTag } from "next/cache"
import { env } from "@/env.mjs"
import createClient from "openapi-fetch"
import { paths } from "types/productsetservice"

const productSetServiceClient = createClient<paths>({
  baseUrl: env.PRODUCTSETSERVICE_BASEURL,
  next: { tags: ["productset"] },
})

export async function getAllProductSets() {
  try {
    const res = await productSetServiceClient.GET("/api/v1/productset")

    if (res.error) {
      throw new Error("Something went wrong while fetching product sets")
    }

    return {
      error: null,
      data: res.data,
    }
  } catch (error) {
    return { error, data: null }
  }
}

export async function deleteProductSet(id: number) {
  const res = await productSetServiceClient.DELETE("/api/v1/productset/{id}", {
    params: {
      path: {
        id,
      },
    },
  })

  if (res.error) {
    throw new Error("Something went wrong while deleting a product set")
  }

  revalidateTag("productset")
}

export async function createProductSet(
  tenant: string,
  name: string,
  query: string,
  productIds: string[]
) {
  const res = await productSetServiceClient.POST("/api/v1/productset", {
    body: {
      tenant,
      name,
      query,
      productIds,
    },
  })

  if (res.error) {
    throw new Error("Something went wrong while creating a product set")
  }

  revalidateTag("productset")
}
