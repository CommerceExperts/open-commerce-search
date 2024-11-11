"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { zodResolver } from "@hookform/resolvers/zod"
import { Check, ChevronsUpDown, CircleHelp, Edit, Trash2 } from "lucide-react"
import { useFieldArray, useForm } from "react-hook-form"
import * as z from "zod"

import { Facet, Field, facetTypes, facetValueOrders } from "@/types/config"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
} from "../ui/command"
import { Popover, PopoverContent, PopoverTrigger } from "../ui/popover"
import { Switch } from "../ui/switch"
import { Textarea } from "../ui/textarea"

const formSchema = z.object({
  label: z.string().min(1),
  type: z
    .any()
    .optional()
    .transform((value) => {
      if (value === "") {
        return undefined
      }
      return value
    }),
  "source-field": z.string().min(1),
  "optimal-value-count": z
    .any()
    .optional()
    .transform((value) => {
      if (value === "") {
        return undefined
      }
      const parsedNumber = Number(value)
      return isNaN(parsedNumber) ? value : parsedNumber
    }),
  "show-unselected-options": z.optional(z.coerce.boolean()),
  "multi-select": z.optional(z.coerce.boolean()),
  order: z
    .any()
    .optional()
    .transform((value) => {
      if (value === "") {
        return undefined
      }
      const parsedNumber = Number(value)
      return isNaN(parsedNumber) ? value : parsedNumber
    }),
  "value-order": z.optional(z.enum(["COUNT", "ALPHANUM_ASC", "ALPHANUM_DESC"])),
  excludeFromFacetLimit: z.optional(z.coerce.boolean()),
  "prefer-variant-on-filter": z.optional(z.coerce.boolean()),
  "min-facet-coverage": z
    .any()
    .optional()
    .transform((value) => {
      if (value === "") {
        return undefined
      }
      const parsedNumber = Number(value)
      return isNaN(parsedNumber) ? value : parsedNumber
    }),
  "min-value-count": z
    .any()
    .optional()
    .transform((value) => {
      if (value === "") {
        return undefined
      }
      const parsedNumber = Number(value)
      return isNaN(parsedNumber) ? value : parsedNumber
    }),
  "filter-dependencies": z
    .array(
      z.object({
        value: z
          .string()
          .min(1, { message: "Please enter a filter dependency." }),
      })
    )
    .optional(),
  "meta-data": z.optional(z.string()),
})

type EditFacetButtonProps = {
  onSubmit: (values: Facet) => void
  facet: Facet
  fields: Field[]
}

export default function EditFacetButton({
  onSubmit,
  facet,
  fields,
}: EditFacetButtonProps) {
  const [dialogOpen, setDialogOpen] = useState(false)

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
  })

  const [sourceFieldSearchValue, setSourceFieldSearchValue] = useState("")
  const [sourceFieldPopoverOpen, setSourceFieldPopoverOpen] = useState(false)

  const {
    fields: filterDependenciesFields,
    append: filterDependenciesAppend,
    remove: filterDependenciesRemove,
  } = useFieldArray({
    name: "filter-dependencies",
    control: form.control,
  })

  useEffect(() => {
    form.setValue("label", facet?.label!)
    form.setValue("type", facet?.["type"]!)
    form.setValue("source-field", facet?.["source-field"]!)
    form.setValue("optimal-value-count", facet?.["optimal-value-count"]!)
    form.setValue(
      "show-unselected-options",
      facet?.["show-unselected-options"]!
    )
    form.setValue(
      "show-unselected-options",
      facet?.["show-unselected-options"]!
    )
    form.setValue("multi-select", facet?.["multi-select"]!)
    form.setValue("order", facet?.["order"]!)
    form.setValue("value-order", facet?.["value-order"]!)
    form.setValue("excludeFromFacetLimit", facet?.["excludeFromFacetLimit"]!)
    form.setValue(
      "prefer-variant-on-filter",
      facet?.["prefer-variant-on-filter"]!
    )
    form.setValue("min-facet-coverage", facet?.["min-facet-coverage"]!)
    form.setValue("min-value-count", facet?.["min-value-count"]!)
    form.setValue(
      "meta-data",
      JSON.stringify(facet?.["meta-data"], null, 2) ?? "{}"
    )
    form.setValue(
      "filter-dependencies",
      facet?.["filter-dependencies"]?.map((_filterDependency) => ({
        value: _filterDependency,
      }))!
    )
  }, [dialogOpen, form, facet])

  return (
    <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
      <DialogTrigger>
        <Edit className="w-4 cursor-pointer" />
      </DialogTrigger>
      <DialogContent className="max-h-screen overflow-y-scroll lg:max-w-screen-sm">
        <DialogHeader>
          <DialogTitle>Edit a facet</DialogTitle>
          <DialogDescription>
            You are editing the &quot;{facet["source-field"]}&quot; facet.
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form
            onSubmit={form.handleSubmit((values) => {
              let parsedMetaData: { [key: string]: any } = {}
              try {
                parsedMetaData = JSON.parse(values["meta-data"] ?? "") as {
                  [key: string]: any
                }
              } catch {
                form.setError("meta-data", {
                  message: "Invalid JSON.",
                })
                return
              }

              onSubmit({
                ...values,
                "filter-dependencies": values["filter-dependencies"]?.map(
                  (_filterDependency) => _filterDependency.value
                ),
                "meta-data": parsedMetaData,
              } as Facet)
              setDialogOpen(false)
            })}
            className="space-y-8"
          >
            <FormField
              control={form.control}
              name="label"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Label</FormLabel>
                  <FormControl>
                    <Input placeholder="Enter label" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="source-field"
              render={({ field }) => (
                <FormItem className="flex flex-col">
                  <FormLabel>Source field</FormLabel>
                  <Popover
                    open={sourceFieldPopoverOpen}
                    onOpenChange={setSourceFieldPopoverOpen}
                  >
                    <PopoverTrigger asChild>
                      <FormControl>
                        <Button
                          variant="outline"
                          role="combobox"
                          className={cn(
                            "w-[400px] justify-between",
                            !field.value && "text-muted-foreground"
                          )}
                        >
                          {field.value ?? "Select source field"}
                          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
                        </Button>
                      </FormControl>
                    </PopoverTrigger>
                    <PopoverContent className="w-[400px] p-0">
                      <Command>
                        <CommandInput
                          value={sourceFieldSearchValue}
                          onValueChange={setSourceFieldSearchValue}
                          placeholder="Search or enter custom source field..."
                        />
                        <CommandEmpty className="m-1 p-0">
                          {sourceFieldSearchValue.trim().length > 0 && (
                            <div
                              onClick={() => {
                                form.setValue(
                                  "source-field",
                                  sourceFieldSearchValue
                                )
                                setSourceFieldPopoverOpen(false)
                              }}
                              className="w-full cursor-pointer break-all p-2 text-center text-sm hover:bg-secondary"
                            >
                              Use {`"${sourceFieldSearchValue}"`} as source
                              field
                            </div>
                          )}
                        </CommandEmpty>
                        <CommandGroup>
                          {fields.map((_field) => (
                            <CommandItem
                              value={_field.name}
                              key={_field.name}
                              onSelect={() => {
                                if (_field.name) {
                                  form.setValue("source-field", _field.name)
                                }
                              }}
                            >
                              <Check
                                className={cn(
                                  "mr-2 h-4 w-4",
                                  field.value === _field.name
                                    ? "opacity-100"
                                    : "opacity-0"
                                )}
                              />
                              {_field.name}
                            </CommandItem>
                          ))}
                        </CommandGroup>
                      </Command>
                    </PopoverContent>
                  </Popover>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="type"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Type</FormLabel>
                  <FormControl>
                    <Select
                      onValueChange={(value) => form.setValue("type", value)}
                      value={
                        field.value
                          ? (field.value as unknown as string).toLowerCase()
                          : undefined
                      }
                    >
                      <SelectTrigger className="w-[180px]">
                        <SelectValue placeholder="Type" />
                      </SelectTrigger>
                      <SelectContent>
                        {facetTypes.map((item) => (
                          <SelectItem key={item} value={item}>
                            {item}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="optimal-value-count"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Optimal value count</FormLabel>
                  <FormControl>
                    <Input placeholder="Enter optimal value count" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="show-unselected-options"
              render={({ field }) => (
                <FormItem className="flex flex-row items-center justify-between rounded-lg border p-4">
                  <div className="space-y-0.5">
                    <FormLabel className="text-base">
                      Show unselected options
                    </FormLabel>
                    <FormDescription>
                      If you do not toggle the switch the value will not be set.
                    </FormDescription>
                  </div>
                  <FormControl>
                    <Switch
                      checked={field.value}
                      onCheckedChange={field.onChange}
                      aria-readonly
                    />
                  </FormControl>
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="multi-select"
              render={({ field }) => (
                <FormItem className="flex flex-row items-center justify-between rounded-lg border p-4">
                  <div className="space-y-0.5">
                    <FormLabel className="text-base">Is multiselect</FormLabel>
                    <FormDescription>
                      If you do not toggle the switch the value will not be set.
                    </FormDescription>
                  </div>
                  <FormControl>
                    <Switch
                      checked={field.value}
                      onCheckedChange={field.onChange}
                      aria-readonly
                    />
                  </FormControl>
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="value-order"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Value order</FormLabel>
                  <FormControl>
                    <Select
                      onValueChange={(val) =>
                        form.setValue(
                          "value-order",
                          val as "COUNT" | "ALPHANUM_ASC" | "ALPHANUM_DESC"
                        )
                      }
                      value={field.value}
                    >
                      <SelectTrigger className="w-[180px]">
                        <SelectValue placeholder="Type" />
                      </SelectTrigger>
                      <SelectContent>
                        {facetValueOrders.map((facetValueOrder) => (
                          <SelectItem
                            key={facetValueOrder}
                            value={facetValueOrder}
                          >
                            {facetValueOrder}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="excludeFromFacetLimit"
              render={({ field }) => (
                <FormItem className="flex flex-row items-center justify-between rounded-lg border p-4">
                  <div className="space-y-0.5">
                    <FormLabel className="text-base">
                      Exclude from facet limit
                    </FormLabel>
                    <FormDescription>
                      If you do not toggle the switch the value will not be set.
                    </FormDescription>
                  </div>
                  <FormControl>
                    <Switch
                      checked={field.value}
                      onCheckedChange={field.onChange}
                      aria-readonly
                    />
                  </FormControl>
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="prefer-variant-on-filter"
              render={({ field }) => (
                <FormItem className="flex flex-row items-center justify-between rounded-lg border p-4">
                  <div className="space-y-0.5">
                    <FormLabel className="text-base">
                      Prefer variant on filter
                    </FormLabel>
                    <FormDescription>
                      If you do not toggle the switch the value will not be set.
                    </FormDescription>
                  </div>
                  <FormControl>
                    <Switch
                      checked={field.value}
                      onCheckedChange={field.onChange}
                      aria-readonly
                    />
                  </FormControl>
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="min-facet-coverage"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Min facet coverage</FormLabel>
                  <FormControl>
                    <Input placeholder="Enter Min facet coverage" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="min-value-count"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Min value count</FormLabel>
                  <FormControl>
                    <Input placeholder="Min value count" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="meta-data"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Metadata</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="Metadata in string map format"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <div>
              {filterDependenciesFields.map((field, index) => (
                <FormField
                  control={form.control}
                  key={field.id}
                  name={`filter-dependencies.${index}.value`}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel
                        className={cn(index !== 0 && "sr-only", "flex gap-2")}
                      >
                        Filter Dependencies
                        <Link
                          target="_blank"
                          href="https://commerceexperts.github.io/open-commerce-search/apidocs/de/cxp/ocs/config/FacetConfiguration.FacetConfig.html#setFilterDependencies(java.lang.String...)"
                        >
                          <CircleHelp className="h-4 w-4" />
                        </Link>
                      </FormLabel>
                      <FormDescription className={cn(index !== 0 && "sr-only")}>
                        Add filter dependencies to your field.
                      </FormDescription>
                      <FormControl>
                        <div className="flex gap-2">
                          <Input {...field} />
                          <Button
                            onClick={() => filterDependenciesRemove(index)}
                            variant="outline"
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              ))}
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="mt-2"
                onClick={() => filterDependenciesAppend({ value: "" })}
              >
                Add filter dependency
              </Button>
            </div>
            <Button type="submit">Save</Button>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
