"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { UploadCloud } from "lucide-react"
import { useForm } from "react-hook-form"
import YAML from "yaml"
import * as z from "zod"

import { Button } from "@/components/ui/button"
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { Input } from "@/components/ui/input"

import { Textarea } from "../ui/textarea"

const formSchema = z.object({
  message: z.string(),
})

type SaveConfigurationFormProps = {
  onSubmit: (values: z.infer<typeof formSchema>) => void
  configuration: any
}

export function SaveConfigurationForm({
  onSubmit,
  configuration,
}: SaveConfigurationFormProps) {
  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      message: "",
    },
  })

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-8">
        <FormField
          control={form.control}
          name="message"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Commit message</FormLabel>
              <FormControl>
                <Input placeholder="Enter commit message" {...field} />
              </FormControl>

              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name="message"
          render={() => (
            <FormItem>
              <FormLabel>Configuration</FormLabel>
              <Textarea
                value={YAML.stringify(configuration, null, 2)}
                readOnly
                className="h-[300px]"
              />
            </FormItem>
          )}
        />
        <Button type="submit" className="flex gap-2 font-bold">
          <UploadCloud className="h-4 w-4" />
          Save & commit
        </Button>
      </form>
    </Form>
  )
}
