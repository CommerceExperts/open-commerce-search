"use client"

import { useEffect, useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { Edit, MoveHorizontal } from "lucide-react"
import { useForm } from "react-hook-form"
import * as z from "zod"

import {
  PluginConfigurationValues,
  QueryProcessingConfigurationItem,
  pluginConfigurationValues,
  pluginConfigurations,
} from "@/types/config"
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
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"

import { Input } from "../ui/input"
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

type EditQueryProcessingConfigurationButtonProps = {
  onSubmit: (values: QueryProcessingConfigurationItem) => void
  item: QueryProcessingConfigurationItem
}

export default function EditQueryProcessingConfigurationButton({
  onSubmit,
  item,
}: EditQueryProcessingConfigurationButtonProps) {
  const [dialogOpen, setDialogOpen] = useState(false)

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
  })

  const codePointLowerBound = form.watch("options.code_point_lower_bound")
  const codePointUpperBound = form.watch("options.code_point_upper_bound")
  const selectedType = form.watch("type")

  useEffect(() => {
    form.setValue(
      "type",
      pluginConfigurations.filter(
        (pluginConfiguration) => pluginConfiguration.value === item.type
      ).length > 0
        ? (item.type as PluginConfigurationValues)
        : "custom"
    )
    form.setValue(
      "customType",
      pluginConfigurations.filter(
        (pluginConfiguration) => pluginConfiguration.value === item.type
      ).length > 0
        ? undefined
        : item.type
    )
    form.setValue("options", item.options)

    if (form.getValues("customType")) {
      form.setValue("customOptions", JSON.stringify(item.options, null, 2))
    }
  }, [dialogOpen, form, item])

  return (
    <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
      <DialogTrigger>
        <Edit className="w-4 cursor-pointer" />
      </DialogTrigger>
      <DialogContent className="max-h-screen overflow-y-scroll lg:max-w-screen-sm">
        <DialogHeader>
          <DialogTitle>Edit a query processing configuration</DialogTitle>
          <DialogDescription>
            You are editing the &quot;
            {item.type}&quot; query processing configuration.
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

              onSubmit({
                options: customOptions ?? values.options,
                type,
              })
              setDialogOpen(false)
            })}
            className="space-y-8"
          >
            {selectedType ===
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
            )}
            <Button type="submit">Save</Button>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
