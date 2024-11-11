"use client"

import { useSearchParams } from "next/navigation"

import { SearchParamsMap } from "@/types/searchParams"

export default function CommitRef() {
  const searchParams = useSearchParams()
  const commitParam = searchParams.get(SearchParamsMap.commit)

  return <span>{commitParam}</span>
}
