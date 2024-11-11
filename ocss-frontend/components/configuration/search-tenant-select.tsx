"use client"

import { useMemo, useState } from "react"
import { usePathname, useRouter, useSearchParams } from "next/navigation"
import _ from "lodash"
import { Check, ChevronsUpDown, Plus, Trash2 } from "lucide-react"
import { useRecoilState } from "recoil"

import { SearchParamsMap } from "@/types/searchParams"
import { searchConfigurationState } from "@/lib/global-state"
import { cn } from "@/lib/utils"
import { Button, buttonVariants } from "@/components/ui/button"
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
} from "@/components/ui/command"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover"
import { Separator } from "@/components/ui/separator"

import { ScrollArea } from "../ui/scroll-area"
import { IndexConfigForm } from "./index-config-form"

export function SearchTenantSelect() {
  const [dialogOpen, setDialogOpen] = useState(false)
  const [searchConfiguration, setSearchConfiguration] = useRecoilState(
    searchConfigurationState
  )
  const tenants = useMemo(
    () => Object.keys(searchConfiguration.ocs?.["tenant-config"] ?? {}),
    [searchConfiguration]
  )
  const router = useRouter()
  const searchParams = useSearchParams()
  const pathname = usePathname()

  const configParam = searchParams.get(SearchParamsMap.config)
  const commitParam = searchParams.get(SearchParamsMap.commit)

  return (
    <Popover>
      <PopoverTrigger asChild className="cursor-pointer">
        <Button
          variant="outline"
          role="combobox"
          className="w-full max-w-xs justify-between"
        >
          <p className="truncate">{configParam || "Default tenant"}</p>
          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[200px] p-0">
        <Command>
          <CommandInput placeholder="Search tenant" />
          <ScrollArea className="h-[200px]">
            <CommandEmpty>No tenant found.</CommandEmpty>
            <CommandGroup>
              {tenants &&
                tenants[0] &&
                tenants.map((tenant) => (
                  <CommandItem
                    onSelect={() => {
                      router.push(
                        pathname +
                          (tenant === configParam && !commitParam
                            ? ""
                            : "/?" +
                              (tenant !== configParam && !commitParam
                                ? new URLSearchParams([
                                    [SearchParamsMap.config, tenant],
                                  ])
                                : tenant === configParam && commitParam
                                ? new URLSearchParams([
                                    [SearchParamsMap.commit, commitParam],
                                  ])
                                : tenant !== configParam && commitParam
                                ? new URLSearchParams([
                                    [SearchParamsMap.commit, commitParam],
                                    [SearchParamsMap.config, tenant],
                                  ])
                                : ""))
                      )
                    }}
                    key={tenant}
                    className="flex justify-between"
                  >
                    <div className="flex items-center gap-1">
                      {tenant == configParam && <Check className="h-4 w-4" />}
                      <p className="max-w-[130px] truncate">{tenant}</p>
                    </div>
                    <Trash2
                      onClick={(e) => {
                        e.stopPropagation()

                        if (configParam == tenant) {
                          router.push(
                            pathname +
                              (commitParam
                                ? "/?" +
                                  new URLSearchParams([
                                    [SearchParamsMap.commit, commitParam],
                                  ])
                                : "")
                          )
                        }

                        const updatedSearchConfiguration =
                          _.cloneDeep(searchConfiguration)

                        delete updatedSearchConfiguration.ocs[
                          "tenant-config"
                        ]?.[tenant]

                        setSearchConfiguration(updatedSearchConfiguration)
                      }}
                      className="h-4 w-4 cursor-pointer text-red-600"
                    />
                  </CommandItem>
                ))}
            </CommandGroup>
          </ScrollArea>

          <Separator className="mt-2" />
          <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
            <DialogTrigger
              className={cn(
                buttonVariants({ variant: "ghost", size: "sm" }),
                "m-1 flex gap-2"
              )}
            >
              {" "}
              <Plus className="h-4 w-4" />
              Create new tenant
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Create new configuration</DialogTitle>
                <DialogDescription className="pb-3">
                  This action creates a custom search configuration which
                  overrides the default search configuraiton.
                </DialogDescription>
                <IndexConfigForm
                  indexConfigs={tenants}
                  onSubmit={(values) => {
                    const updatedSearchConfiguration =
                      _.cloneDeep(searchConfiguration)

                    const duplicatedSearchConfiguration = _.get(
                      searchConfiguration,
                      ["ocs", "tenant-config", values.duplicateFrom ?? ""]
                    )

                    _.set(
                      updatedSearchConfiguration,
                      ["ocs", "tenant-config", values.name],
                      duplicatedSearchConfiguration ?? {}
                    )

                    setSearchConfiguration(updatedSearchConfiguration)

                    setDialogOpen(false)
                  }}
                />
              </DialogHeader>
            </DialogContent>
          </Dialog>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
