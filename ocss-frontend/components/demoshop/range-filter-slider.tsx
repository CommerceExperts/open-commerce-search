import { useState } from "react"
import Link from "next/link"
import { useSearchParams } from "next/navigation"

import { Facet } from "@/types/api"
import { SearchParamsMap } from "@/types/searchParams"
import { cn, deleteURLSearchParams, mergeURLSearchParams } from "@/lib/utils"

import { buttonVariants } from "../ui/button"
import { Input } from "../ui/input"
import { RangeSlider } from "../ui/range-slider"

export default function RangeFilterSlider({
  filterOption,
}: RangeFilterSliderProps) {
  const searchParams = useSearchParams()

  const [sliderValue, setSliderValue] = useState([
    (filterOption.entries?.[0] as any)?.["selectedMin"] ?? 0,
    (filterOption.entries?.[0] as any)?.["selectedMax"] ??
      (filterOption.entries?.[0] as any)["upperBound"],
  ])

  return (
    <div className="space-y-4 pb-2">
      <div className="mb-4 flex items-center gap-8">
        <Input
          type="number"
          value={sliderValue[0]}
          onChange={(e) => {
            const input = parseFloat(e.target.value)
            if (isNaN(input)) {
              setSliderValue((sliderValue) => [0, sliderValue[1]])
            } else {
              setSliderValue((sliderValue) => [input, sliderValue[1]])
            }
          }}
        />
        <span className="select-none">-</span>
        <Input
          type="number"
          value={sliderValue[1]}
          onChange={(e) => {
            const input = parseFloat(e.target.value)
            if (isNaN(input)) {
              setSliderValue((sliderValue) => [sliderValue[0], 1000])
            } else {
              setSliderValue((sliderValue) => [sliderValue[0], input])
            }
          }}
        />
      </div>
      <RangeSlider
        max={(filterOption.entries?.[0] as any)["upperBound"]}
        step={1}
        value={sliderValue}
        onValueChange={(e: any) => setSliderValue(e)}
      />
      <Link
        href={
          "/?" +
          mergeURLSearchParams(
            deleteURLSearchParams(new URLSearchParams(searchParams), [
              filterOption.fieldName ?? "",
              SearchParamsMap.page,
            ]),
            new URLSearchParams([
              [
                filterOption.fieldName ?? "",
                `${sliderValue[0]}-${sliderValue[1]}`,
              ],
            ])
          )
        }
        onClick={(e) => {
          e.stopPropagation()
        }}
        rel="noreferrer"
        className={cn(
          buttonVariants({ variant: "secondary" }),
          "h-8 w-full rounded-full text-xs"
        )}
      >
        Apply
      </Link>
    </div>
  )
}

type RangeFilterSliderProps = {
  filterOption: Facet
}
