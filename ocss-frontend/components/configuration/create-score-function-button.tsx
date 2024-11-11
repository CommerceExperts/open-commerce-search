"use client"

import { useEffect, useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { Plus } from "lucide-react"
import { useForm } from "react-hook-form"
import * as z from "zod"

import {
  Field,
  ScoreFunction,
  ScoreFunctionModifier,
  ScoreFunctionType,
  scoreFunctionModifiers,
  scoreFunctionTypes,
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
} from "@/components/ui/select"

import { Switch } from "../ui/switch"
import { Textarea } from "../ui/textarea"

const formSchema = z.object({
  type: z.enum(scoreFunctionTypes),
  field: z.optional(z.string()),
  weight: z
    .any()
    .optional()
    .transform((value) => {
      if (value === "") {
        return undefined
      }
      const parsedNumber = Number(value)
      return isNaN(parsedNumber) ? value : parsedNumber
    }),
  options: z.optional(
    z.object({
      USE_FOR_VARIANTS: z.optional(z.boolean()),
      RANDOM_SEED: z.optional(z.string()),
      MISSING: z.optional(
        z
          .any()
          .optional()
          .transform((value) => {
            if (value === "") {
              return undefined
            }
            const parsedNumber = Number(value)
            return isNaN(parsedNumber) ? value : parsedNumber
          })
      ),
      MODIFIER: z.optional(z.enum(scoreFunctionModifiers)),
      FACTOR: z
        .any()
        .optional()
        .transform((value) => {
          if (value === "") {
            return undefined
          }
          const parsedNumber = Number(value)
          return isNaN(parsedNumber) ? value : parsedNumber
        }),
      SCRIPT_CODE: z.optional(z.string()),
      ORIGIN: z
        .any()
        .optional()
        .transform((value) => {
          if (value === "") {
            return undefined
          }
          const parsedNumber = Number(value)
          return isNaN(parsedNumber) ? value : parsedNumber
        }),
      SCALE: z
        .any()
        .optional()
        .transform((value) => {
          if (value === "") {
            return undefined
          }
          const parsedNumber = Number(value)
          return isNaN(parsedNumber) ? value : parsedNumber
        }),
      OFFSET: z
        .any()
        .optional()
        .transform((value) => {
          if (value === "") {
            return undefined
          }
          const parsedNumber = Number(value)
          return isNaN(parsedNumber) ? value : parsedNumber
        }),
      DECAY: z
        .any()
        .optional()
        .transform((value) => {
          if (value === "") {
            return undefined
          }
          const parsedNumber = Number(value)
          return isNaN(parsedNumber) ? value : parsedNumber
        }),
    })
  ),
})

type CreateScoreFunctionButtonProps = {
  onSubmit: (values: ScoreFunction) => void
  fields: Field[]
}

export default function CreateScoreFunctionButton({
  onSubmit,
  fields,
}: CreateScoreFunctionButtonProps) {
  const [dialogOpen, setDialogOpen] = useState(false)

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {},
  })

  useEffect(() => {
    form.reset()
  }, [dialogOpen, form])

  const selectedType = form.watch("type")

  return (
    <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
      <DialogTrigger
        className={cn(
          buttonVariants({ variant: "outline" }),
          "flex w-full gap-2"
        )}
      >
        <Plus className="h-4 w-4" />
        Create score function
      </DialogTrigger>
      <DialogContent className="max-h-screen overflow-y-scroll lg:max-w-screen-sm">
        <DialogHeader>
          <DialogTitle>Create a score function</DialogTitle>
          <DialogDescription>
            You are creating a new score function.
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form
            onSubmit={form.handleSubmit((values) => {
              onSubmit(values)
              setDialogOpen(false)
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
                      onValueChange={(value: ScoreFunctionType) =>
                        form.setValue("type", value)
                      }
                      value={field.value}
                    >
                      <SelectTrigger className="w-[180px]">
                        <SelectValue placeholder="Type" />
                      </SelectTrigger>
                      <SelectContent>
                        {scoreFunctionTypes.map((type) => (
                          <SelectItem key={type} value={type}>
                            {type}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            {selectedType === "WEIGHT" ? (
              <>
                <FormField
                  control={form.control}
                  name="weight"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Weight</FormLabel>
                      <FormControl>
                        <Input
                          type="number"
                          placeholder="Enter weight"
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </>
            ) : selectedType === "RANDOM_SCORE" ? (
              <>
                <FormField
                  control={form.control}
                  name="field"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Field</FormLabel>
                      <FormControl>
                        <Select
                          onValueChange={(value) =>
                            form.setValue("field", value)
                          }
                          value={field.value}
                        >
                          <SelectTrigger className="w-[180px]">
                            <SelectValue placeholder="Field" />
                          </SelectTrigger>
                          <SelectContent>
                            {fields.map((field) => (
                              <SelectItem
                                key={field.name}
                                value={field.name ?? ""}
                              >
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
                  name="options.RANDOM_SEED"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Seed</FormLabel>
                      <FormControl>
                        <Input placeholder="Enter seed" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </>
            ) : selectedType === "FIELD_VALUE_FACTOR" ? (
              <>
                <FormField
                  control={form.control}
                  name="field"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Field</FormLabel>
                      <FormControl>
                        <Select
                          onValueChange={(value) =>
                            form.setValue("field", value)
                          }
                          value={field.value}
                        >
                          <SelectTrigger className="w-[180px]">
                            <SelectValue placeholder="Field" />
                          </SelectTrigger>
                          <SelectContent>
                            {fields.map((field) => (
                              <SelectItem
                                key={field.name}
                                value={field.name ?? ""}
                              >
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
                  name="options.FACTOR"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Factor</FormLabel>
                      <FormControl>
                        <Input
                          type="number"
                          placeholder="Enter factor"
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="options.MODIFIER"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Modifier</FormLabel>
                      <FormControl>
                        <Select
                          onValueChange={(value: ScoreFunctionModifier) =>
                            form.setValue("options.MODIFIER", value)
                          }
                          value={field.value}
                        >
                          <SelectTrigger className="w-[180px]">
                            <SelectValue placeholder="Modifier" />
                          </SelectTrigger>
                          <SelectContent>
                            {scoreFunctionModifiers.map((modifier) => (
                              <SelectItem key={modifier} value={modifier}>
                                {modifier}
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
                  name="options.MISSING"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Missing</FormLabel>
                      <FormControl>
                        <Input
                          type="number"
                          placeholder="Enter missing"
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </>
            ) : selectedType === "DECAY_EXP" ||
              selectedType === "DECAY_GAUSS" ||
              selectedType === "DECAY_LINEAR" ? (
              <>
                <FormField
                  control={form.control}
                  name="field"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Field</FormLabel>
                      <FormControl>
                        <Select
                          onValueChange={(value) =>
                            form.setValue("field", value)
                          }
                          value={field.value}
                        >
                          <SelectTrigger className="w-[180px]">
                            <SelectValue placeholder="Field" />
                          </SelectTrigger>
                          <SelectContent>
                            {fields.map((field) => (
                              <SelectItem
                                key={field.name}
                                value={field.name ?? ""}
                              >
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
                  name="options.ORIGIN"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Origin</FormLabel>
                      <FormControl>
                        <Input
                          type="number"
                          placeholder="Enter origin"
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="options.SCALE"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Scale</FormLabel>
                      <FormControl>
                        <Input
                          type="number"
                          placeholder="Enter scale"
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="options.OFFSET"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Offset</FormLabel>
                      <FormControl>
                        <Input
                          type="number"
                          placeholder="Enter offset"
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="options.DECAY"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Decay</FormLabel>
                      <FormControl>
                        <Input
                          type="number"
                          placeholder="Enter decay"
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </>
            ) : selectedType === "SCRIPT_SCORE" ? (
              <>
                <FormField
                  control={form.control}
                  name="options.SCRIPT_CODE"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Script code</FormLabel>
                      <FormControl>
                        <Textarea placeholder="Enter script code" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </>
            ) : (
              <></>
            )}

            <FormField
              control={form.control}
              name="options.USE_FOR_VARIANTS"
              render={({ field }) => (
                <FormItem className="flex flex-row items-center justify-between rounded-lg border p-4">
                  <div className="space-y-0.5">
                    <FormLabel className="text-base">
                      Use for variants
                    </FormLabel>
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
            <Button type="submit">Create</Button>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
