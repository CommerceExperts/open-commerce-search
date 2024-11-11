"use client"

import { useRouter, useSearchParams } from "next/navigation"
import { Server } from "lucide-react"

import { SearchParamsMap } from "@/types/searchParams"
import { cn } from "@/lib/utils"
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
import { buttonVariants } from "../ui/button"

type TenantSelectButtonProps = {
  tenants: string[]
  query: string
}

export default function TenantSelectButton({
  tenants,
  query,
}: TenantSelectButtonProps) {
  const router = useRouter()
  const searchParams = useSearchParams()

  const tenantParam = searchParams.get(SearchParamsMap.tenant)

  return (
    <Popover>
      <PopoverTrigger
        asChild
        className={cn(buttonVariants({ variant: "outline" }), "cursor-pointer")}
      >
        <span className="flex flex-row gap-2">
          <Server className="h-4 w-4" />
          Change tenant
        </span>
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
                          [SearchParamsMap.query, query],
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
  )
}
