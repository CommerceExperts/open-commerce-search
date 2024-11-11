"use client"

import { ArrowDownWideNarrow, ArrowUpNarrowWide, Trash2 } from "lucide-react"

import { Field, SortOption } from "@/types/config"
import { cn } from "@/lib/utils"
import { buttonVariants } from "@/components/ui/button"

import CreateSortOptionButton from "./create-sort-option-button"
import EditSortOptionButton from "./edit-sort-option-button"

type SortConfigurationProps = {
  onSortOptionCreation: (newSortOption: SortOption) => void
  onSortOptionEdit: (newSortOption: SortOption, index: number) => void
  onSortOptionDelete: (newSortOption: SortOption, index: number) => void
  sortOptions: SortOption[]
  fields: Field[]
}

export function SortConfiguration({
  onSortOptionCreation,
  onSortOptionDelete,
  onSortOptionEdit,
  sortOptions,
  fields,
}: SortConfigurationProps) {
  fields = fields.filter((field) =>
    field.usage?.map((usage) => usage.trim().toLowerCase()).includes("sort")
  )

  return (
    <>
      <h1 className="text-md font-medium">Sort options</h1>
      <ul className="space-y-2">
        {sortOptions.map((sortOption, index) => (
          <li
            key={sortOption.label}
            className={cn(
              buttonVariants({ variant: "outline" }),
              "grid w-full grid-cols-[92%,1fr]"
            )}
          >
            <p className="mr-4 flex items-center gap-2 overflow-hidden text-left">
              {sortOption.label} ({sortOption.field})
              {sortOption.order === "DESC" ? (
                <ArrowDownWideNarrow className="h-4 w-4" />
              ) : (
                <ArrowUpNarrowWide className="h-4 w-4" />
              )}
            </p>
            <div className="flex gap-4">
              <EditSortOptionButton
                index={index}
                sortOption={sortOption}
                sortOptions={sortOptions}
                onSubmit={(newSortOption) =>
                  onSortOptionEdit(newSortOption, index)
                }
                fields={fields}
              />
              <Trash2
                onClick={() => onSortOptionDelete(sortOption, index)}
                className="w-4 cursor-pointer text-red-600"
              />
            </div>
          </li>
        ))}
        <CreateSortOptionButton
          onSubmit={onSortOptionCreation}
          sortOptions={sortOptions}
          fields={fields}
        />
      </ul>
    </>
  )
}
