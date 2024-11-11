"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { Plus } from "lucide-react"
import { useForm } from "react-hook-form"
import * as z from "zod"

import { Button } from "@/components/ui/button"
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
  name: z.string().min(1, {
    message: "Name must be at least 1 character.",
  }),
  duplicateFrom: z.optional(z.string()),
})

type IndexConfigFormProps = {
  onSubmit: (values: z.infer<typeof formSchema>) => void
  indexConfigs: string[]
}

export function IndexConfigForm({
  onSubmit,
  indexConfigs,
}: IndexConfigFormProps) {
  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      name: "",
    },
  })

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-8">
        <FormField
          control={form.control}
          name="name"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Configuration name</FormLabel>
              <FormControl>
                <Input placeholder="Enter configuration name" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name="duplicateFrom"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Duplicate</FormLabel>
              <FormControl>
                <Select
                  onValueChange={(value) =>
                    form.setValue("duplicateFrom", value)
                  }
                  value={field.value}
                >
                  <SelectTrigger className="w-[180px]">
                    <SelectValue placeholder="Select duplicate" />
                  </SelectTrigger>
                  <SelectContent>
                    {indexConfigs.map((indexConfig) => (
                      <SelectItem key={indexConfig} value={indexConfig}>
                        {indexConfig}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </FormControl>

              <FormDescription>
                Select a configuration to duplicate or a blank configuration
                will be used.
              </FormDescription>

              <FormMessage />
            </FormItem>
          )}
        />
        <Button type="submit" className="flex gap-2">
          <Plus className="h-4 w-4" />
          Create new configuration
        </Button>
      </form>
    </Form>
  )
}
