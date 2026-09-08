import { useEffect, useState } from "react"
import Link from "next/link"
import { useSearchParams } from "next/navigation"
import { Minus, Plus } from "lucide-react"

import { Facet, FacetEntry } from "@/types/api"
import { SearchParamsMap } from "@/types/searchParams"
import { deleteURLSearchParams, mergeURLSearchParams } from "@/lib/utils"
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible"

import { CheckboxDisplay } from "../ui/checkbox"
import { Label } from "../ui/label"

function HierarchicalEntry({ entry, filterOption }: HierarchicalEntryProps) {
  const searchParams = useSearchParams()

  const [isOpen, setIsOpen] = useState(false)

  useEffect(() => {
    setIsOpen(entry.selected ?? false)
  }, [entry])

  const pathDelimiter = "/"
  const path = (entry as any)["path"]?.split(pathDelimiter)

  const href =
    "/?" +
    (entry.selected
      ? path.length > 1
        ? mergeURLSearchParams(
            deleteURLSearchParams(new URLSearchParams(searchParams), [
              filterOption.fieldName ?? "",
              SearchParamsMap.page,
            ]),
            new URLSearchParams([
              [
                filterOption.fieldName ?? "",
                path.slice(0, -1).join(pathDelimiter) ?? "",
              ],
            ])
          )
        : deleteURLSearchParams(new URLSearchParams(searchParams), [
            filterOption.fieldName ?? "",
            SearchParamsMap.page,
          ])
      : mergeURLSearchParams(
          deleteURLSearchParams(new URLSearchParams(searchParams), [
            filterOption.fieldName ?? "",
            SearchParamsMap.page,
          ]),
          new URLSearchParams([
            [filterOption.fieldName ?? "", (entry as any)["path"] ?? ""],
          ])
        ))

  // The filter link and the expand/collapse control are siblings, never nested.
  // Radix renders both CollapsibleTrigger and Checkbox as <button>, so putting
  // this row inside the trigger produced invalid <button><a><button> markup and
  // a hydration error.
  const entryRow = (
    <div className="flex items-center gap-2">
      <Link href={href} className="flex items-center space-x-2">
        <CheckboxDisplay checked={entry.selected} />
        <Label className="break-all text-left">{entry.key}</Label>
      </Link>
      <p className="text-xs">{entry.docCount}</p>
    </div>
  )

  return (
    <>
      {(entry as any)["children"] && (entry as any)["children"].length > 0 ? (
        <Collapsible open={isOpen} onOpenChange={setIsOpen}>
          <div className="flex w-full items-center justify-between">
            {entryRow}

            <CollapsibleTrigger
              aria-label={`${isOpen ? "Collapse" : "Expand"} ${entry.key}`}
            >
              {isOpen ? (
                <Minus className="size-4" />
              ) : (
                <Plus className="size-4" />
              )}
            </CollapsibleTrigger>
          </div>
          <CollapsibleContent className="pl-5 pt-2">
            <ul className="flex flex-col gap-2">
              {(entry as any)["children"].map((_entry: FacetEntry) => (
                <HierarchicalEntry
                  key={_entry.key}
                  entry={_entry}
                  filterOption={filterOption}
                />
              ))}
            </ul>
          </CollapsibleContent>
        </Collapsible>
      ) : (
        entryRow
      )}
    </>
  )
}

type HierarchicalEntryProps = {
  entry: FacetEntry
  filterOption: Facet
}

export default function HierarchicalFilter({
  filterOption,
}: HierarchicalFilterProps) {
  return (
    <ul className="flex flex-col gap-2 pb-2">
      {filterOption.entries?.map((entry) => (
        <li key={entry.key}>
          <HierarchicalEntry entry={entry} filterOption={filterOption} />
        </li>
      ))}
    </ul>
  )
}

type HierarchicalFilterProps = {
  filterOption: Facet
}
