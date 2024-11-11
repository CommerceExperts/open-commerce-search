"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { useSearchParams } from "next/navigation"

import { Facet } from "@/types/api"
import { SearchParamsMap } from "@/types/searchParams"
import { cn, includeURLSearchParams } from "@/lib/utils"
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Collapsible, CollapsibleContent } from "@/components/ui/collapsible"

import { buttonVariants } from "../ui/button"
import CheckboxFilter from "./checkbox-filter"
import HierarchicalFilter from "./hierarchical-filter"
import IntervalFilter from "./interval-filter"
import RangeFilterSlider from "./range-filter-slider"

export function FilterOptions({ filterOptions, open }: FilterOptionsProps) {
  const searchParams = useSearchParams()

  const [isOpen, setIsOpen] = useState(open)
  const [opened, setOpened] = useState<string[]>([])

  useEffect(() => {
    const filteredFilterOptions = filterOptions
      .filter((filterOption) => filterOption.isFiltered)
      .map((filterOption) => filterOption.fieldName ?? "")

    setOpened(filteredFilterOptions)
  }, [filterOptions])

  return (
    <Card className="h-min">
      <CardHeader onClick={() => setIsOpen((isOpen) => !isOpen)}>
        <div className="flex items-center justify-between">
          <CardTitle className="text-xl">Filter</CardTitle>

          <div className="flex gap-2">
            <Link
              href={
                "/?" +
                includeURLSearchParams(new URLSearchParams(searchParams), [
                  SearchParamsMap.tenant,
                  SearchParamsMap.query,
                  SearchParamsMap.sort,
                ])
              }
              onClick={(e) => {
                e.stopPropagation()
              }}
              rel="noreferrer"
              className={cn(buttonVariants(), "h-8 w-16 rounded-full text-xs")}
            >
              Reset
            </Link>
          </div>
        </div>
      </CardHeader>
      <Collapsible open={isOpen}>
        <CollapsibleContent>
          <CardContent>
            <Accordion value={opened} onValueChange={setOpened} type="multiple">
              {filterOptions.map((filterOption) => (
                <AccordionItem
                  key={filterOption.fieldName}
                  value={filterOption.fieldName ?? ""}
                >
                  <AccordionTrigger className="break-all text-left">
                    {(filterOption?.meta as any)?.label ??
                      filterOption?.fieldName}
                  </AccordionTrigger>
                  <AccordionContent className="p-1">
                    {filterOption.type === "term" ? (
                      <CheckboxFilter filterOption={filterOption} />
                    ) : filterOption.type === "range" ? (
                      <RangeFilterSlider filterOption={filterOption} />
                    ) : filterOption.type === "hierarchical" ? (
                      <HierarchicalFilter filterOption={filterOption} />
                    ) : filterOption.type === "interval" ? (
                      <IntervalFilter filterOption={filterOption} />
                    ) : (
                      <></>
                    )}
                  </AccordionContent>
                </AccordionItem>
              ))}
            </Accordion>
          </CardContent>
        </CollapsibleContent>
      </Collapsible>
    </Card>
  )
}

type FilterOptionsProps = {
  open: boolean
  filterOptions: Facet[]
}
