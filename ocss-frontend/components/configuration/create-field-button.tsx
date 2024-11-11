"use client"

import { useEffect, useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { Plus, Trash2 } from "lucide-react"
import { useFieldArray, useForm } from "react-hook-form"
import * as z from "zod"

import {
  Field,
  FieldUsage,
  fieldLevels,
  fieldTypes,
  fieldUsages,
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

const formSchema = z.object({
  name: z.string().min(1),
  type: z.enum(["string", "number", "category", "id"]),
  "field-level": z.enum(["master", "variant", "both"]),
  "source-names": z
    .array(
      z.object({
        value: z.string().min(1, { message: "Please enter a usage." }),
      })
    )
    .optional(),
  usage: z
    .array(
      z.object({
        value: z.string().min(1, { message: "Please enter a usage." }),
      })
    )
    .optional(),
})

type CreateFieldButtonProps = {
  onSubmit: (values: Field) => void
  fields: Field[]
}

export default function CreateFieldButton({
  onSubmit,
  fields,
}: CreateFieldButtonProps) {
  const [dialogOpen, setDialogOpen] = useState(false)

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      name: "",
      "source-names": [],
      usage: [],
    },
  })

  const {
    fields: usageFields,
    append: usageAppend,
    remove: usageRemove,
  } = useFieldArray({
    name: "usage",
    control: form.control,
  })

  const {
    fields: sourceNamesFields,
    append: sourceNamesAppend,
    remove: sourceNamesRemove,
  } = useFieldArray({
    name: "source-names",
    control: form.control,
  })

  useEffect(() => {
    form.reset()
  }, [dialogOpen, form])

  return (
    <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
      <DialogTrigger
        className={cn(
          buttonVariants({ variant: "outline" }),
          "flex w-full gap-2"
        )}
      >
        <Plus className="h-4 w-4" />
        Create field
      </DialogTrigger>
      <DialogContent className="max-h-screen overflow-y-scroll lg:max-w-screen-sm">
        <DialogHeader>
          <DialogTitle>Create a field</DialogTitle>
          <DialogDescription>You are creating a new field.</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form
            onSubmit={form.handleSubmit((values) => {
              if (fields.find((field) => field.name == values.name)) {
                form.setError("name", {
                  message: "Name is already being used by another field.",
                })
                return
              }

              onSubmit({
                ...values,
                usage: values.usage?.map((_usage) => _usage.value),
                "source-names": values["source-names"]?.map(
                  (_usage) => _usage.value
                ),
              } as Field)
              setDialogOpen(false)
            })}
            className="space-y-8"
          >
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Name</FormLabel>
                  <FormControl>
                    <Input placeholder="Enter name" {...field} />
                  </FormControl>
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
                      onValueChange={(val) =>
                        form.setValue(
                          "type",
                          val as "string" | "number" | "category" | "id"
                        )
                      }
                      value={field.value}
                    >
                      <SelectTrigger className="w-[180px]">
                        <SelectValue placeholder="Type" />
                      </SelectTrigger>
                      <SelectContent>
                        {fieldTypes.map((fieldType) => (
                          <SelectItem key={fieldType} value={fieldType}>
                            {fieldType}
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
              name="field-level"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Field level</FormLabel>
                  <FormControl>
                    <Select
                      onValueChange={(val) =>
                        form.setValue(
                          "field-level",
                          val as "master" | "variant" | "both"
                        )
                      }
                      value={field.value}
                    >
                      <SelectTrigger className="w-[180px]">
                        <SelectValue placeholder="Field level" />
                      </SelectTrigger>
                      <SelectContent>
                        {fieldLevels.map((fieldLevel) => (
                          <SelectItem key={fieldLevel} value={fieldLevel}>
                            {fieldLevel}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <div>
              {usageFields.map((field, index) => (
                <FormField
                  control={form.control}
                  key={field.id}
                  name={`usage.${index}.value`}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel className={cn(index !== 0 && "sr-only")}>
                        Usages
                      </FormLabel>
                      <FormDescription className={cn(index !== 0 && "sr-only")}>
                        Add usages to your field.
                      </FormDescription>
                      <FormControl>
                        <div className="flex gap-2">
                          <Select
                            onValueChange={(value) => {
                              let usages = form.getValues("usage") ?? []
                              usages[index] = { value }
                              form.setValue("usage", usages)
                            }}
                            value={field.value}
                          >
                            <SelectTrigger className="w-[180px]">
                              <SelectValue placeholder="Type" />
                            </SelectTrigger>
                            <SelectContent>
                              {fieldUsages.map((fieldUsage) => (
                                <SelectItem key={fieldUsage} value={fieldUsage}>
                                  {fieldUsage}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>

                          <Button
                            onClick={() => usageRemove(index)}
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
                onClick={() => usageAppend({ value: "search" as FieldUsage })}
              >
                Add usage
              </Button>
            </div>
            <div>
              {sourceNamesFields.map((field, index) => (
                <FormField
                  control={form.control}
                  key={field.id}
                  name={`source-names.${index}.value`}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel className={cn(index !== 0 && "sr-only")}>
                        Source names
                      </FormLabel>
                      <FormDescription className={cn(index !== 0 && "sr-only")}>
                        Add source names to your field.
                      </FormDescription>
                      <FormControl>
                        <div className="flex gap-2">
                          <Input {...field} />
                          <Button
                            onClick={() => sourceNamesRemove(index)}
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
                onClick={() => sourceNamesAppend({ value: "" })}
              >
                Add source name
              </Button>
            </div>
            <Button type="submit">Create</Button>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
