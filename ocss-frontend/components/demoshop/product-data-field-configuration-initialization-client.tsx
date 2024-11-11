"use client"

import { useEffect } from "react"
import { useRecoilState } from "recoil"

import { ProductDataFieldConfiguration } from "@/types/config"
import { productDataFieldConfigurationState } from "@/lib/global-state"

type ProductDataFieldConfigurationInitializationClientProps = {
  initialProductDataFieldConfiguration: ProductDataFieldConfiguration[]
}

export default function ProductDataFieldConfigurationInitializationClient({
  initialProductDataFieldConfiguration,
}: ProductDataFieldConfigurationInitializationClientProps) {
  const [productDataFieldConfiguration, setProductDataFieldConfiguration] =
    useRecoilState(productDataFieldConfigurationState)

  useEffect(() => {
    setProductDataFieldConfiguration(initialProductDataFieldConfiguration)
  }, [initialProductDataFieldConfiguration, setProductDataFieldConfiguration])

  return <></>
}
