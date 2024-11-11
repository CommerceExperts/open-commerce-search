"use client"

import Link from "next/link"
import { useSearchParams } from "next/navigation"

import { SearchParamsMap } from "@/types/searchParams"
import {
  cn,
  deleteURLSearchParams,
  mergeURLSearchParams,
  range,
} from "@/lib/utils"

import { Icons } from "../misc/icons"
import { Button, buttonVariants } from "../ui/button"

export default function Pagination({ page, total }: PaginationProps) {
  const searchParams = useSearchParams()

  const newSearchParams = (newPage: number) =>
    "/?" +
    mergeURLSearchParams(
      deleteURLSearchParams(new URLSearchParams(searchParams), [
        SearchParamsMap.page,
      ]),
      new URLSearchParams([[SearchParamsMap.page, newPage.toString()]])
    )

  return (
    <>
      {total > 0 && (
        <div className="flex flex-wrap gap-2">
          {page === 1 ? (
            <Button variant="secondary" className="h-9 cursor-not-allowed p-2">
              <Icons.chevronLeft />
            </Button>
          ) : (
            <Link
              href={newSearchParams(page - 1)}
              rel="noreferrer"
              className={cn(
                buttonVariants({ variant: "secondary" }),
                "h-9 p-2"
              )}
            >
              <Icons.chevronLeft />
            </Link>
          )}

          <Link
            href={newSearchParams(1)}
            rel="noreferrer"
            className={cn(
              buttonVariants({ variant: page === 1 ? "default" : "secondary" }),
              "h-9"
            )}
          >
            <p className="text-xs sm:text-sm">{1}</p>
          </Link>

          {page >= 5 && (
            <p className="flex select-none items-baseline px-1">...</p>
          )}

          {range(
            page < 5 ? 2 : page < total - 3 ? page - 1 : total - 3,
            total > 5
              ? page < 5
                ? 5
                : page < total - 3
                ? page + 1
                : total - 1
              : total - 1
          ).map((i) => (
            <Link
              key={i}
              href={newSearchParams(i)}
              rel="noreferrer"
              className={cn(
                buttonVariants({
                  variant: page === i ? "default" : "secondary",
                }),
                "h-9"
              )}
            >
              <p className="text-xs sm:text-sm">{i}</p>
            </Link>
          ))}

          {total > 6 && page < total - 2 && (
            <p className="flex select-none items-baseline px-1">...</p>
          )}

          {total > 1 && (
            <Link
              href={newSearchParams(total)}
              rel="noreferrer"
              className={cn(
                buttonVariants({
                  variant: page === total ? "default" : "secondary",
                }),
                "h-9"
              )}
            >
              <p className="text-xs sm:text-sm">{total}</p>
            </Link>
          )}

          {page === total ? (
            <Button variant="secondary" className="h-9 cursor-not-allowed p-2">
              <Icons.chevronRight />
            </Button>
          ) : (
            <Link
              href={newSearchParams(page + 1)}
              rel="noreferrer"
              className={cn(
                buttonVariants({ variant: "secondary" }),
                "h-9 p-2"
              )}
            >
              <Icons.chevronRight />
            </Link>
          )}
        </div>
      )}
    </>
  )
}

type PaginationProps = {
  page: number
  total: number
}
