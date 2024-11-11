"use client"

import Link from "next/link"
import { usePathname, useRouter, useSearchParams } from "next/navigation"
import { Server, ShoppingCart } from "lucide-react"

import { SearchParamsMap } from "@/types/searchParams"
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
} from "@/components/ui/command"
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover"

import { Icons } from "../misc/icons"

type DemoshopButtonProps = {
  tenants: string[]
}

export default function DemoshopButton({ tenants }: DemoshopButtonProps) {
  const router = useRouter()
  const searchParams = useSearchParams()
  const pathname = usePathname()

  const tenantParam = searchParams.get(SearchParamsMap.tenant)

  return (
    <>
      {pathname == "/" ? (
        <Popover>
          <PopoverTrigger asChild className="cursor-pointer">
            <p className="text-md flex items-center gap-2 font-semibold text-muted-foreground sm:text-sm">
              <Server className="h-5 w-5 sm:h-4 sm:w-4" />
              <span className="hidden sm:block">
                {tenantParam || "Select tenant"}
              </span>
            </p>
          </PopoverTrigger>
          <PopoverContent className="w-[200px] p-0">
            <Command>
              <CommandInput placeholder="Search tenant..." />
              <CommandEmpty>No tenants found.</CommandEmpty>
              <CommandGroup>
                {tenants &&
                  tenants[0] &&
                  tenants.map((tenant) => (
                    <CommandItem
                      onSelect={() => {
                        router.push(
                          "/" +
                            "?" +
                            new URLSearchParams([
                              [SearchParamsMap.tenant, tenant],
                            ]).toString()
                        )
                      }}
                      key={tenant}
                    >
                      {tenant == tenantParam && (
                        <Icons.check className="mr-2 h-4 w-4" />
                      )}
                      {tenant}
                    </CommandItem>
                  ))}
              </CommandGroup>
            </Command>
          </PopoverContent>
        </Popover>
      ) : (
        <Link href="/">
          <p className="text-md flex items-center gap-2 font-semibold text-muted-foreground sm:text-sm">
            <ShoppingCart className="h-5 w-5 sm:h-4 sm:w-4" />
            <span className="hidden sm:block">Demoshop</span>
          </p>
        </Link>
      )}
    </>
  )
}
