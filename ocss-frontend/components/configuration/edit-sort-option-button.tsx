"use client"

import { useEffect, useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { Edit } from "lucide-react"
import { useForm } from "react-hook-form"
import * as z from "zod"

import {
  Field,
  SortOption,
  SortOptionMissing,
  SortOptionOrder,
  sortOptionMissings,
  sortOptionOrders,
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

import { Input } from "../ui/input"

const formSchema = z.object({
  label: z.string().min(1),
  field: z.string().min(1),
  missing: z.optional(z.enum(sortOptionMissings)),
  order: z.enum(sortOptionOrders),
})

type EditSortOptionButtonProps = {
  onSubmit: (values: SortOption) => void
  sortOption: SortOption
  fields: Field[]
  sortOptions: SortOption[]
  index: number
}

export default function EditSortOptionButton({
  onSubmit,
  sortOption,
  fields,
  sortOptions,
  index,
}: EditSortOptionButtonProps) {
  const [dialogOpen, setDialogOpen] = useState(false)

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
  })

  useEffect(() => {
    form.setValue("label", sortOption?.label!)
    form.setValue("field", sortOption?.field!)
    form.setValue("missing", sortOption?.missing!)
    form.setValue("order", sortOption?.order!)
  }, [dialogOpen, form, sortOption])

  return (
    <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
      <DialogTrigger>
        <Edit className="w-4 cursor-pointer" />
      </DialogTrigger>
      <DialogContent className="max-h-screen overflow-y-scroll lg:max-w-screen-sm">
        <DialogHeader>
          <DialogTitle>Edit a sort option</DialogTitle>
          <DialogDescription>
            You are editing the &quot;{sortOption.label}&quot; sort option.
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form
            onSubmit={form.handleSubmit((values) => {
              if (
                sortOptions.find(
                  (sortOption, _index) =>
                    index != _index && sortOption.label == values.label
                )
              ) {
                form.setError("label", {
                  message:
                    "Label is already being used by another sort option.",
                })
                return
              }

              onSubmit(values)
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
              name="field"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Field</FormLabel>
                  <FormControl>
                    <Select
                      onValueChange={(value) => form.setValue("field", value)}
                      value={field.value}
                    >
                      <SelectTrigger className="w-[180px]">
                        <SelectValue placeholder="Field" />
                      </SelectTrigger>
                      <SelectContent>
                        {fields.map((field) => (
                          <SelectItem key={field.name} value={field.name ?? ""}>
                            {field.name}
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
              name="order"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Order</FormLabel>
                  <FormControl>
                    <Select
                      onValueChange={(value) =>
                        form.setValue("order", value as SortOptionOrder)
                      }
                      value={field.value}
                    >
                      <SelectTrigger className="w-[180px]">
                        <SelectValue placeholder="Order" />
                      </SelectTrigger>
                      <SelectContent>
                        {sortOptionOrders.map((sortOptionOrder) => (
                          <SelectItem
                            key={sortOptionOrder}
                            value={sortOptionOrder}
                          >
                            {sortOptionOrder}
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
              name="missing"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Missing</FormLabel>
                  <FormControl>
                    <Select
                      onValueChange={(value) =>
                        form.setValue("missing", value as SortOptionMissing)
                      }
                      value={field.value}
                    >
                      <SelectTrigger className="w-[180px]">
                        <SelectValue placeholder="Missing" />
                      </SelectTrigger>
                      <SelectContent>
                        {sortOptionMissings.map((sortOptionMissing) => (
                          <SelectItem
                            key={sortOptionMissing}
                            value={sortOptionMissing}
                          >
                            {sortOptionMissing}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <Button type="submit">Save</Button>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
