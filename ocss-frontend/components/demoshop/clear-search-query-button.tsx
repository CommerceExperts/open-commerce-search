"use client"

import { useRouter } from "next/navigation"
import { Eraser } from "lucide-react"

import { SearchParamsMap } from "@/types/searchParams"

import { Button } from "../ui/button"

type ClearSearchQueryButtonProps = {
  tenant: string
}

export default function ClearSearchQueryButton({
  tenant,
}: ClearSearchQueryButtonProps) {
  const router = useRouter()

  return (
    <Button
      onClick={() => {
        router.push(
          "/" +
            "?" +
            new URLSearchParams([
              [SearchParamsMap.query, ""],
              [SearchParamsMap.tenant, tenant],
            ]).toString()
        )
      }}
      variant="outline"
      className="flex gap-2"
    >
      <Eraser className="h-4 w-4" />
      Clear search query
    </Button>
  )
}
