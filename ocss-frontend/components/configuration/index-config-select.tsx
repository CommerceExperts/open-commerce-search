"use client"

import { useMemo, useState } from "react"
import { usePathname, useRouter, useSearchParams } from "next/navigation"
import _ from "lodash"
import { Check, ChevronsUpDown, Plus, Trash2 } from "lucide-react"
import { useRecoilState } from "recoil"

import { SearchParamsMap } from "@/types/searchParams"
import { indexerConfigurationState } from "@/lib/global-state"
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

export function IndexConfigSelect() {
  const [dialogOpen, setDialogOpen] = useState(false)
  const [indexerConfiguration, setIndexerConfiguration] = useRecoilState(
    indexerConfigurationState
  )
  const indexConfigs = useMemo(
    () => Object.keys(indexerConfiguration.ocs?.["index-config"] ?? {}),
    [indexerConfiguration]
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
          <p className="truncate">{configParam || "Default index config"}</p>
          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[200px] p-0">
        <Command>
          <CommandInput placeholder="Search index config" />
          <ScrollArea className="h-[200px]">
            <CommandEmpty>No index config found.</CommandEmpty>
            <CommandGroup>
              {indexConfigs &&
                indexConfigs[0] &&
                indexConfigs.map((indexConfig) => (
                  <CommandItem
                    onSelect={() => {
                      router.push(
                        pathname +
                          (indexConfig === configParam && !commitParam
                            ? ""
                            : "/?" +
                              (indexConfig !== configParam && !commitParam
                                ? new URLSearchParams([
                                    [SearchParamsMap.config, indexConfig],
                                  ])
                                : indexConfig === configParam && commitParam
                                ? new URLSearchParams([
                                    [SearchParamsMap.commit, commitParam],
                                  ])
                                : indexConfig !== configParam && commitParam
                                ? new URLSearchParams([
                                    [SearchParamsMap.commit, commitParam],
                                    [SearchParamsMap.config, indexConfig],
                                  ])
                                : ""))
                      )
                    }}
                    key={indexConfig}
                    className="flex justify-between"
                  >
                    <div className="flex items-center gap-1">
                      {indexConfig == configParam && (
                        <Check className="h-4 w-4" />
                      )}
                      <p className="max-w-[130px] truncate">{indexConfig}</p>
                    </div>
                    <Trash2
                      onClick={(e) => {
                        e.stopPropagation()

                        if (configParam == indexConfig) {
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

                        const updatedIndexerConfiguration =
                          _.cloneDeep(indexerConfiguration)

                        delete updatedIndexerConfiguration.ocs[
                          "index-config"
                        ]?.[indexConfig]

                        setIndexerConfiguration(updatedIndexerConfiguration)
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
              Create new config
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Create new configuration</DialogTitle>
                <DialogDescription className="pb-3">
                  This action creates a custom index configuration which
                  overrides the default index configuraiton.
                </DialogDescription>
                <IndexConfigForm
                  indexConfigs={indexConfigs}
                  onSubmit={(values) => {
                    const updatedIndexerConfiguration =
                      _.cloneDeep(indexerConfiguration)

                    const duplicatedIndexerConfiguration = _.get(
                      indexerConfiguration,
                      ["ocs", "index-config", values.duplicateFrom ?? ""]
                    )

                    _.set(
                      updatedIndexerConfiguration,
                      ["ocs", "index-config", values.name],
                      duplicatedIndexerConfiguration ?? {}
                    )

                    setIndexerConfiguration(updatedIndexerConfiguration)

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
