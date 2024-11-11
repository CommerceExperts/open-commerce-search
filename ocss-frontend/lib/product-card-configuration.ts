"use server"

import { cookies } from "next/headers"

import { ProductDataFieldConfiguration } from "@/types/config"
import { InternalCookies } from "@/types/cookies"

export async function setProductDataFieldConfigurationCookie(
  items: ProductDataFieldConfiguration[]
) {
  cookies().set(
    InternalCookies.productDataFieldConfiguration,
    JSON.stringify(items)
  )
}

export async function getProductDataFieldConfigurationFromCookie() {
  return JSON.parse(
    cookies().get(InternalCookies.productDataFieldConfiguration)?.value ?? "[]"
  ) as ProductDataFieldConfiguration[]
}
