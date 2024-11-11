import Link from "next/link"
import { useSearchParams } from "next/navigation"

import { Facet } from "@/types/api"
import { SearchParamsMap } from "@/types/searchParams"
import {
  deleteURLSearchParams,
  deleteURLSearchParamsWithValue,
  mergeURLSearchParams,
} from "@/lib/utils"

import { Checkbox } from "../ui/checkbox"
import { Label } from "../ui/label"

export default function IntervalFilter({ filterOption }: IntervalFilterProps) {
  const searchParams = useSearchParams()

  return (
    <div className="flex flex-col gap-2 pb-2">
      {filterOption.entries?.map((entry) => (
        <div key={entry.id} className="flex items-center gap-2">
          <Link
            href={
              "/?" +
              (entry.selected
                ? deleteURLSearchParams(
                    deleteURLSearchParamsWithValue(
                      new URLSearchParams(searchParams),
                      [filterOption.fieldName ?? ""],
                      `${entry?.lowerBound}-${entry?.upperBound}` ?? ""
                    ),
                    [SearchParamsMap.page]
                  )
                : mergeURLSearchParams(
                    deleteURLSearchParams(new URLSearchParams(searchParams), [
                      SearchParamsMap.page,
                    ]),
                    new URLSearchParams([
                      [
                        filterOption.fieldName ?? "",
                        `${entry?.lowerBound}-${entry?.upperBound}` ?? "",
                      ],
                    ])
                  ))
            }
            className="flex items-center space-x-2"
          >
            <Checkbox id={entry.key} checked={entry.selected} />
            <Label htmlFor={entry.key}>{entry.key}</Label>
          </Link>
          <p className="text-xs">{entry.docCount}</p>
        </div>
      ))}
    </div>
  )
}

type IntervalFilterProps = {
  filterOption: Facet
}
