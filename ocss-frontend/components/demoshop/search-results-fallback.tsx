import { env } from "@/env.mjs"
import { Bookmark, Info, Pen } from "lucide-react"

import { defaultSortOption } from "@/lib/search-api"
import { cn, range } from "@/lib/utils"
import { Card, CardHeader, CardTitle } from "@/components/ui/card"
import SortSelect from "@/components/demoshop/sort-select"

import { Button, buttonVariants } from "../ui/button"
import { Skeleton } from "../ui/skeleton"

type SearchResultsFallbackProps = {
  searchResultsPerPage: number
}

export default function SearchResultsFallback({
  searchResultsPerPage,
}: SearchResultsFallbackProps) {
  return (
    <section className="container grid max-w-[1600px] items-center gap-6 pb-8 pt-6 md:py-10">
      <div className="grid gap-8 lg:grid-cols-3 xl:grid-cols-4">
        <div className="flex flex-col gap-2">
          <div>
            <Card className="h-min">
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle className="text-xl">Filter</CardTitle>

                  <div className="flex gap-2">
                    <p
                      className={cn(
                        buttonVariants(),
                        "h-8 w-16 cursor-pointer rounded-full text-xs"
                      )}
                    >
                      Reset
                    </p>
                  </div>
                </div>
              </CardHeader>
            </Card>
          </div>
        </div>

        <div className="lg:col-span-2 xl:col-span-3">
          <div className="mb-4 flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
            <p>Searching ...</p>

            <div className="flex gap-2">
              <SortSelect
                sortOptions={[defaultSortOption]}
                selectedSortOption={defaultSortOption.field}
              />
              <Button disabled={true} className="h-10 w-10 p-1">
                <Pen className="h-4 w-4" />
              </Button>
              {!!env.PRODUCTSETSERVICE_BASEURL && (
                <Button disabled={true} className="h-10 w-10 p-1">
                  <Bookmark className="h-4 w-4" />
                </Button>
              )}
              <Button disabled={true} className="h-10 w-10 p-1">
                <Info className="h-4 w-4" />
              </Button>
            </div>
          </div>

          <ul className="grid w-full grid-cols-1 gap-4 lg:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
            {range(1, searchResultsPerPage).map((i) => (
              <Skeleton key={i} className="h-[400px]" />
            ))}
          </ul>
        </div>
      </div>
    </section>
  )
}
