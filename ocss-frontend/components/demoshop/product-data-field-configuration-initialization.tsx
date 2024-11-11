import { getProductDataFieldConfigurationFromCookie } from "@/lib/product-card-configuration"

import ProductDataFieldConfigurationInitializationClient from "./product-data-field-configuration-initialization-client"

export default async function ProductDataFieldConfigurationInitialization() {
  const initialProductDataFieldConfiguration =
    await getProductDataFieldConfigurationFromCookie()

  return (
    <>
      <ProductDataFieldConfigurationInitializationClient
        initialProductDataFieldConfiguration={
          initialProductDataFieldConfiguration
        }
      />
    </>
  )
}
