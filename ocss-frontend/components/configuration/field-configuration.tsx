"use client"

import { Trash2 } from "lucide-react"

import { Field } from "@/types/config"
import { cn } from "@/lib/utils"
import { buttonVariants } from "@/components/ui/button"

import CreateFieldButton from "./create-field-button"
import EditFieldButton from "./edit-field-button"

type FieldConfigurationProps = {
  onFieldCreation: (newField: Field) => void
  onFieldEdit: (newField: Field, index: number) => void
  onFieldDelete: (field: Field, index: number) => void
  heading: string
  fields: Field[]
}

export function FieldConfiguration({
  onFieldCreation,
  onFieldDelete,
  onFieldEdit,
  heading,
  fields,
}: FieldConfigurationProps) {
  return (
    <>
      <h1 className="text-md font-medium">{heading}</h1>
      <ul className="space-y-2">
        {fields.map((field, index) => (
          <li
            key={field.name}
            className={cn(
              buttonVariants({ variant: "outline" }),
              "grid w-full grid-cols-[92%,1fr]"
            )}
          >
            <p className="mr-4 overflow-hidden text-left">{field.name}</p>
            <div className="flex gap-4">
              <EditFieldButton
                fields={fields}
                index={index}
                field={field}
                onSubmit={(newField) => onFieldEdit(newField, index)}
              />
              <Trash2
                onClick={() => onFieldDelete(field, index)}
                className="w-4 cursor-pointer text-red-600"
              />
            </div>
          </li>
        ))}
        <CreateFieldButton fields={fields} onSubmit={onFieldCreation} />
      </ul>
    </>
  )
}
