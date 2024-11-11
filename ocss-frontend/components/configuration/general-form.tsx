"use client"

import { useEffect } from "react"
import { useSearchParams } from "next/navigation"
import { zodResolver } from "@hookform/resolvers/zod"
import _ from "lodash"
import { useForm } from "react-hook-form"
import { useRecoilState } from "recoil"
import * as z from "zod"

import { SearchParamsMap } from "@/types/searchParams"
import { indexerConfigurationState } from "@/lib/global-state"
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
import { Switch } from "@/components/ui/switch"

const formSchema = z.object({
  "replica-count": z
    .any()
    .optional()
    .transform((value) => {
      if (value === "") {
        return undefined
      }
      const parsedNumber = Number(value)
      return isNaN(parsedNumber) ? value : parsedNumber
    }),
  "minimum-document-count": z
    .any()
    .optional()
    .transform((value) => {
      if (value === "") {
        return undefined
      }
      const parsedNumber = Number(value)
      return isNaN(parsedNumber) ? value : parsedNumber
    }),
  "wait-time-ms-for-healthy-index": z
    .any()
    .optional()
    .transform((value) => {
      if (value === "") {
        return undefined
      }
      const parsedNumber = Number(value)
      return isNaN(parsedNumber) ? value : parsedNumber
    }),
  // TODO: Add regex
  "refresh-interval": z
    .any()
    .optional()
    .transform((value) => {
      if (value === "") {
        return undefined
      }
      return value
    }),
  "use-default-config": z.optional(z.coerce.boolean()),
})

type GeneralFormProps = {
  indexConfig: string
}

export function GeneralForm({ indexConfig }: GeneralFormProps) {
  const [indexerConfiguration, setIndexerConfiguration] = useRecoilState(
    indexerConfigurationState
  )
  const searchParams = useSearchParams()

  const configParam = searchParams.get(SearchParamsMap.config)

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: _.merge(
      {
        "minimum-document-count": "",
        "refresh-interval": "",
        "replica-count": "",
        "use-default-config": true,
        "wait-time-ms-for-healthy-index": "",
      },
      configParam
        ? indexerConfiguration?.ocs["index-config"]?.[configParam]?.[
            "indexer-settings"
          ]
        : indexerConfiguration?.ocs["default-index-config"]?.[
            "indexer-settings"
          ]
    ),
  })

  useEffect(() => {
    if (configParam) {
      form.setValue(
        "replica-count",
        indexerConfiguration.ocs["index-config"]?.[configParam]?.[
          "indexer-settings"
        ]?.["replica-count"] ?? ""
      )
      form.setValue(
        "minimum-document-count",
        indexerConfiguration.ocs["index-config"]?.[configParam]?.[
          "indexer-settings"
        ]?.["minimum-document-count"] ?? ""
      )
      form.setValue(
        "refresh-interval",
        indexerConfiguration.ocs["index-config"]?.[configParam]?.[
          "indexer-settings"
        ]?.["refresh-interval"] ?? ""
      )
      form.setValue(
        "use-default-config",
        indexerConfiguration.ocs["index-config"]?.[configParam]?.[
          "indexer-settings"
        ]?.["use-default-config"] ?? true
      )
      form.setValue(
        "wait-time-ms-for-healthy-index",
        indexerConfiguration.ocs["index-config"]?.[configParam]?.[
          "indexer-settings"
        ]?.["wait-time-ms-for-healthy-index"] ?? ""
      )
    } else {
      form.setValue(
        "replica-count",
        indexerConfiguration.ocs["default-index-config"]?.[
          "indexer-settings"
        ]?.["replica-count"] ?? ""
      )
      form.setValue(
        "minimum-document-count",
        indexerConfiguration.ocs["default-index-config"]?.[
          "indexer-settings"
        ]?.["minimum-document-count"] ?? ""
      )
      form.setValue(
        "refresh-interval",
        indexerConfiguration.ocs["default-index-config"]?.[
          "indexer-settings"
        ]?.["refresh-interval"] ?? ""
      )
      form.setValue(
        "use-default-config",
        indexerConfiguration.ocs["default-index-config"]?.[
          "indexer-settings"
        ]?.["use-default-config"] ?? true
      )
      form.setValue(
        "wait-time-ms-for-healthy-index",
        indexerConfiguration.ocs["default-index-config"]?.[
          "indexer-settings"
        ]?.["wait-time-ms-for-healthy-index"] ?? ""
      )
    }
  }, [configParam])

  useEffect(() => {
    const subscription = form.watch((value) => {
      const updatedIndexerConfiguration = _.cloneDeep(indexerConfiguration)

      if (configParam) {
        _.set(
          updatedIndexerConfiguration,
          ["ocs", "index-config", configParam, "indexer-settings"],
          formSchema.parse(value)
        )
      } else {
        _.set(
          updatedIndexerConfiguration,
          ["ocs", "default-index-config", "indexer-settings"],
          formSchema.parse(value)
        )
      }

      setIndexerConfiguration(updatedIndexerConfiguration)
    })
    return () => subscription.unsubscribe()
  }, [form, configParam])

  return (
    <Form {...form}>
      <form className="space-y-8">
        <FormField
          control={form.control}
          name="replica-count"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Replica count</FormLabel>
              <FormControl>
                <Input
                  type="number"
                  placeholder="Enter replica count"
                  {...field}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name="refresh-interval"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Refresh interval</FormLabel>
              <FormControl>
                <Input
                  type="text"
                  placeholder="Enter refresh interval"
                  {...field}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name="minimum-document-count"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Minimum document count</FormLabel>
              <FormControl>
                <Input
                  type="number"
                  placeholder="Enter minimum document count"
                  {...field}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name="wait-time-ms-for-healthy-index"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Wait time for healthy index (ms)</FormLabel>
              <FormControl>
                <Input
                  type="number"
                  placeholder="Enter wait time for healthy index"
                  {...field}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name="use-default-config"
          render={({ field }) => (
            <FormItem className="flex flex-row items-center justify-between rounded-lg border p-4">
              <div className="space-y-0.5">
                <FormLabel className="text-base">Use default config</FormLabel>
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
      </form>
    </Form>
  )
}
