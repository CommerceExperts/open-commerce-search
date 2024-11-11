"use client"

import { useTransition } from "react"
import { useSearchParams } from "next/navigation"
import { zodResolver } from "@hookform/resolvers/zod"
import { Plus } from "lucide-react"
import { useForm } from "react-hook-form"
import { toast } from "sonner"
import { z } from "zod"

import { SearchParamsMap } from "@/types/searchParams"
import { createProductSet } from "@/lib/productsetservice"
import { Button } from "@/components/ui/button"
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"

import { Checkbox } from "../ui/checkbox"
import { Input } from "../ui/input"

const formSchema = z.object({
  tenant: z.string().min(1),
  name: z.string(),

  includeHeroProductIds: z.boolean(),
  heroProductIds: z.array(z.string()),

  includeQuery: z.boolean(),
  query: z.string(),
})

type CreateBookmarkFormProps = {
  tenant: string
}

export function CreateBookmarkForm({ tenant }: CreateBookmarkFormProps) {
  const [isPending, startTransition] = useTransition()
  const searchParams = useSearchParams()
  const query: string = searchParams.get(SearchParamsMap.query) ?? ""
  const heroProductIds: string[] =
    searchParams
      .getAll(SearchParamsMap.heroProductIds)
      ?.flatMap((item) => item.split(",")) ?? []

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      query,
      name: "",
      tenant,
      heroProductIds,
      includeHeroProductIds: heroProductIds.length > 0,
      includeQuery: query.length > 0,
    },
  })

  const formValues = form.watch()

  function onSubmit(values: z.infer<typeof formSchema>) {
    if (!(values.includeHeroProductIds || values.includeQuery)) {
      toast.error("You must select at least one parameter to bookmark.")
      return
    }

    startTransition(() => {
      createProductSet(
        tenant,
        formValues.name,
        formValues.includeQuery ? formValues.query : "",
        formValues.includeHeroProductIds ? formValues.heroProductIds : []
      )
    })
  }

  return (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit(onSubmit)}
        className="space-y-3 text-left"
      >
        <FormField
          control={form.control}
          name="name"
          render={({ field }) => (
            <FormItem>
              <FormLabel className="font-bold">Name</FormLabel>
              <FormControl>
                <Input placeholder="Enter optional name" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name="includeHeroProductIds"
          render={({ field }) => (
            <FormItem className="flex flex-row items-start space-x-3 space-y-0 rounded-md border p-4 shadow">
              <FormControl>
                <Checkbox
                  checked={field.value}
                  onCheckedChange={field.onChange}
                />
              </FormControl>
              <div className="space-y-1 leading-none">
                <FormLabel className="break-all">
                  <span className="font-bold">
                    Include the following hero product IDs in bookmark?
                  </span>
                  <ol className="list-decimal pl-4 pt-1">
                    {formValues.heroProductIds.map((heroProductId) => (
                      <li key={heroProductId}>{heroProductId}</li>
                    ))}
                  </ol>
                </FormLabel>
              </div>
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name="includeQuery"
          render={({ field }) => (
            <FormItem className="flex flex-row items-start space-x-3 space-y-0 rounded-md border p-4 shadow">
              <FormControl>
                <Checkbox
                  checked={field.value}
                  onCheckedChange={field.onChange}
                />
              </FormControl>
              <div className="space-y-1 leading-none">
                <FormLabel className="break-all">
                  <span className="font-bold">
                    Include the query {`"${formValues.query}"`} in bookmark?
                  </span>
                </FormLabel>
              </div>
            </FormItem>
          )}
        />
        <Button
          type="submit"
          disabled={isPending}
          className="flex items-center gap-1"
        >
          <Plus className="h-4 w-4" />
          Create bookmark
        </Button>
      </form>
    </Form>
  )
}
