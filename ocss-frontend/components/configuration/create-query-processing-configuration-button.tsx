"use client"

import { useEffect, useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { MoveHorizontal, Plus } from "lucide-react"
import { useForm } from "react-hook-form"
import * as z from "zod"

import {
  PluginConfigurationValues,
  QueryProcessingConfigurationItem,
  pluginConfigurationValues,
  pluginConfigurations,
} from "@/types/config"
import { cn } from "@/lib/utils"
import { Button, buttonVariants } from "@/components/ui/button"
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
} from "../ui/select"
import { Textarea } from "../ui/textarea"

const formSchema = z.object({
  type: z.enum(pluginConfigurationValues),
  options: z.optional(
    z.object({
      code_point_lower_bound: z.optional(z.string()),
      code_point_upper_bound: z.optional(z.string()),

      apiKey: z.optional(z.string()),
      tenant: z.optional(z.string()),
    })
  ),

  customType: z.optional(z.string()),
  customOptions: z.optional(z.string()),
})

type CreateQueryProcessingConfigurationButtonProps = {
  onSubmit: (values: QueryProcessingConfigurationItem) => void
  items?: QueryProcessingConfigurationItem[]
}

export default function CreateQueryProcessingConfigurationButton({
  onSubmit,
  items,
}: CreateQueryProcessingConfigurationButtonProps) {
  const [dialogOpen, setDialogOpen] = useState(false)

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {},
  })

  useEffect(() => {
    form.reset()
  }, [dialogOpen, form])

  const [alreadySelectedType, setAlreadySelectedType] = useState(false)

  const selectedType = form.watch("type")
  const codePointLowerBound = form.watch("options.code_point_lower_bound")
  const codePointUpperBound = form.watch("options.code_point_upper_bound")

  useEffect(() => {
    form.resetField("customType")
    form.resetField("customOptions")
    form.resetField("options.apiKey")
    form.resetField("options.code_point_lower_bound")
    form.resetField("options.code_point_upper_bound")
    form.resetField("options.tenant")
  }, [selectedType])

  return (
    <Dialog
      open={dialogOpen}
      onOpenChange={(open) => {
        setDialogOpen(open)

        if (!open) {
          setAlreadySelectedType(false)
        }
      }}
    >
      <DialogTrigger
        className={cn(
          buttonVariants({ variant: "outline" }),
          "flex w-full gap-2"
        )}
      >
        <Plus className="h-4 w-4" />
        Create query processing configuration
      </DialogTrigger>
      <DialogContent className="max-h-screen overflow-y-scroll lg:max-w-screen-sm">
        <DialogHeader>
          <DialogTitle>Create a query processing configuration</DialogTitle>
          <DialogDescription>
            You are creating a new query processing configuration.
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form
            onSubmit={form.handleSubmit((values) => {
              let customOptions: { [key: string]: any } | undefined
              if (values.customOptions) {
                try {
                  customOptions = JSON.parse(values.customOptions) as {
                    [key: string]: any
                  }
                } catch {
                  return form.setError("customOptions", {
                    message: "Invalid JSON",
                  })
                }
              }

              let type: string
              if (values.type == "custom") {
                if (values.customType == "custom") {
                  form.setError("customType", {
                    message: "Type name cannot be 'custom'.",
                  })
                  return
                }

                if (values.customType && values.customType.trim() !== "") {
                  type = values.customType
                } else {
                  return form.setError("customType", {
                    type: "required",
                    message: "Required",
                  })
                }
              } else {
                type = values.type
              }

              if (items?.find((item) => item.type == type)) {
                if (values.type == "custom") {
                  return form.setError("customType", {
                    message:
                      "Name is already being used by another query processing configuration.",
                  })
                } else {
                  return form.setError("type", {
                    message:
                      "Type is already being used by another query processing configuration.",
                  })
                }
              }

              onSubmit({
                options: customOptions ?? values.options,
                type,
              })
              setDialogOpen(false)
              setAlreadySelectedType(false)
            })}
            className="space-y-8"
          >
            <FormField
              control={form.control}
              name="type"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Type</FormLabel>
                  <FormControl>
                    <Select
                      onValueChange={(value) => {
                        form.setValue(
                          "type",
                          value as PluginConfigurationValues
                        )
                        setAlreadySelectedType(true)
                      }}
                      value={alreadySelectedType ? field.value : undefined}
                    >
                      <SelectTrigger>
                        <SelectValue placeholder="Select type" />
                      </SelectTrigger>
                      <SelectContent>
                        {pluginConfigurations
                          .filter((pluginConfiguration) =>
                            items?.every(
                              (item) => item.type !== pluginConfiguration.value
                            )
                          )
                          .map((pluginConfiguration) => (
                            <SelectItem
                              key={pluginConfiguration.value}
                              value={pluginConfiguration.value}
                            >
                              {pluginConfiguration.label}
                            </SelectItem>
                          ))}
                      </SelectContent>
                    </Select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {alreadySelectedType &&
              (selectedType ===
              "de.cxp.ocs.elasticsearch.query.CodePointFilterUserQueryPreprocessor" ? (
                <>
                  <FormField
                    control={form.control}
                    name="options.code_point_lower_bound"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Lower bound</FormLabel>
                        <FormControl>
                          <div className="flex items-center gap-4">
                            <Input
                              placeholder="Enter lower bound (integer)"
                              type="number"
                              value={codePointLowerBound?.codePointAt(0) ?? ""}
                              onChange={(e) => {
                                if (e.target.value.trim() == "") {
                                  return form.setValue(
                                    "options.code_point_lower_bound",
                                    ""
                                  )
                                }

                                let integer = parseInt(e.target.value)
                                if (isNaN(integer)) {
                                  integer = 0
                                }

                                form.setValue(
                                  "options.code_point_lower_bound",
                                  String.fromCodePoint(integer)
                                )
                              }}
                            />
                            <MoveHorizontal className="h-8 w-8" />
                            <Input
                              placeholder="Enter lower bound (character)"
                              {...field}
                            />
                          </div>
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name="options.code_point_upper_bound"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Upper bound</FormLabel>
                        <FormControl>
                          <div className="flex items-center gap-4">
                            <Input
                              placeholder="Enter upper bound (integer)"
                              type="number"
                              value={codePointUpperBound?.codePointAt(0) ?? ""}
                              onChange={(e) => {
                                if (e.target.value.trim() == "") {
                                  return form.setValue(
                                    "options.code_point_upper_bound",
                                    ""
                                  )
                                }

                                let integer = parseInt(e.target.value)

                                if (isNaN(integer)) {
                                  integer = 0
                                }

                                form.setValue(
                                  "options.code_point_upper_bound",
                                  String.fromCodePoint(integer)
                                )
                              }}
                            />
                            <MoveHorizontal className="h-8 w-8" />
                            <Input
                              placeholder="Enter upper bound (character)"
                              {...field}
                            />
                          </div>
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </>
              ) : selectedType ===
                "io.searchhub.smartquery.SmartQueryPreprocessor" ? (
                <>
                  <FormField
                    control={form.control}
                    name="options.apiKey"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>API Key</FormLabel>
                        <FormControl>
                          <Input placeholder="Enter API Key" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name="options.tenant"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>searchHub Tenant</FormLabel>
                        <FormControl>
                          <Input placeholder="Enter tenant" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </>
              ) : selectedType === "custom" ? (
                <>
                  <FormField
                    control={form.control}
                    name="customType"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Full canonical classname</FormLabel>
                        <FormControl>
                          <Input
                            placeholder="Enter full canonical classname"
                            {...field}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name="customOptions"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Options</FormLabel>
                        <FormControl>
                          <Textarea
                            placeholder="Enter options (in JSON format)"
                            {...field}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </>
              ) : (
                <></>
              ))}

            <Button type="submit">Create</Button>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
