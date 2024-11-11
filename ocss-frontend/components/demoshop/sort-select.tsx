"use client"

import { useRouter, useSearchParams } from "next/navigation"

import { Sorting } from "@/types/api"
import { SearchParamsMap } from "@/types/searchParams"
import { defaultSortOption } from "@/lib/search-api"
import { deleteURLSearchParams, mergeURLSearchParams } from "@/lib/utils"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

type SortSelectProps = {
  sortOptions: Sorting[]
  selectedSortOption: string
}

export default function SortSelect({
  sortOptions,
  selectedSortOption,
}: SortSelectProps) {
  const router = useRouter()
  const searchParams = useSearchParams()

  return (
    <div className="flex items-center gap-4">
      <p>Sort by</p>
      <Select
        defaultValue={selectedSortOption || defaultSortOption.field}
        onValueChange={(e) =>
          router.push(
            "/?" +
              mergeURLSearchParams(
                deleteURLSearchParams(new URLSearchParams(searchParams), [
                  SearchParamsMap.sort,
                ]),
                new URLSearchParams([[SearchParamsMap.sort, e]])
              ).toString()
          )
        }
      >
        <SelectTrigger className="w-[180px]">
          <SelectValue placeholder="Sort by" />
        </SelectTrigger>
        <SelectContent>
          {sortOptions.map((sortOption) => (
            <SelectItem key={sortOption.field} value={sortOption.field ?? ""}>
              {sortOption.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}
