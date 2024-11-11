"use client"

import { useState, useTransition } from "react"
import { useRouter } from "next/navigation"
import { ColumnDef } from "@tanstack/react-table"
import { Bookmark as BookmarkIcon } from "lucide-react"
import { MoreHorizontal } from "lucide-react"

import { components } from "@/types/productsetservice"
import { SearchParamsMap } from "@/types/searchParams"
import { deleteProductSet } from "@/lib/productsetservice"
import { cn } from "@/lib/utils"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTrigger,
} from "@/components/ui/dialog"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"

import { Button, buttonVariants } from "../ui/button"
import { CreateBookmarkForm } from "./create-bookmark-form"
import { DataTable } from "./data-table"

type BookmarksButtonProps = {
  tenant: string
  bookmarks: components["schemas"]["ProductSetResponseDTO"][]
}

export default function BookmarksButton({
  tenant,
  bookmarks,
}: BookmarksButtonProps) {
  const [isPending, startTransition] = useTransition()
  const router = useRouter()
  const [dialogOpen, setDialogOpen] = useState(false)

  const columns: ColumnDef<components["schemas"]["ProductSetResponseDTO"]>[] = [
    {
      accessorKey: "id",
      header: "ID",
    },
    {
      accessorKey: "tenant",
      header: "Tenant",
    },
    {
      accessorKey: "name",
      header: "Name",
    },
    {
      accessorKey: "query",
      header: "Query",
    },
    {
      accessorKey: "productIds",
      header: "Product IDs",
    },
    {
      id: "actions",
      cell: ({ row }) => {
        const bookmark = row.original

        return (
          <DropdownMenu>
            <DropdownMenuTrigger asChild disabled={isPending}>
              <Button variant="ghost" className="h-8 w-8 p-0">
                <span className="sr-only">Open menu</span>
                <MoreHorizontal className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuLabel>Actions</DropdownMenuLabel>
              <DropdownMenuItem
                onClick={() => {
                  router.push(
                    "/?" +
                      new URLSearchParams([
                        [SearchParamsMap.tenant, bookmark.tenant ?? " "],
                        [
                          SearchParamsMap.heroProductIds,
                          bookmark.productIds && bookmark.productIds.length > 0
                            ? bookmark.productIds.join(",")
                            : "",
                        ],
                        [
                          SearchParamsMap.query,
                          bookmark.query ? bookmark.query : "",
                        ],
                      ])
                  )
                  setDialogOpen(false)
                }}
              >
                Select bookmark
              </DropdownMenuItem>
              <DropdownMenuItem
                onClick={() => {
                  startTransition(() => {
                    if (!bookmark.id) {
                      throw new Error("Bookmark has no id.")
                    }
                    deleteProductSet(bookmark.id)
                  })
                }}
              >
                Delete bookmark
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        )
      },
    },
  ]

  return (
    <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
      <DialogTrigger>
        <div className={cn(buttonVariants(), "h-10 w-10 p-1")}>
          <BookmarkIcon className="h-4 w-4" />
        </div>
      </DialogTrigger>
      <DialogContent className="max-h-screen overflow-y-scroll lg:max-w-screen-lg">
        <DialogHeader>
          <DialogDescription className="space-y-4">
            <div className="space-y-2">
              <h3 className="text-lg font-bold text-primary">Bookmarks</h3>
              <DataTable columns={columns} data={bookmarks} />
            </div>

            <div className="space-y-2">
              <h3 className="text-lg font-bold text-primary">
                Create new bookmark
              </h3>
              <CreateBookmarkForm tenant={tenant} />
            </div>
          </DialogDescription>
        </DialogHeader>
      </DialogContent>
    </Dialog>
  )
}
