import React from "react"
import { UniqueIdentifier } from "@dnd-kit/core"
import { useSortable } from "@dnd-kit/sortable"
import { CSS } from "@dnd-kit/utilities"
import { GripVertical } from "lucide-react"

import { cn } from "@/lib/utils"

import { buttonVariants } from "../ui/button"

type ConfigSortableItemProps<T> = {
  i: number
  item: T
  onEdit: (item: T, index: number) => void
  onDelete: (item: T, index: number) => void
  label: string
  actionButtons: React.ReactNode
} & {
  identifier: UniqueIdentifier
} & React.ComponentProps<"div">

export function ConfigSortableItem<T>({
  i,
  item,
  onDelete,
  onEdit,
  identifier,
  label,
  actionButtons,
  ...props
}: ConfigSortableItemProps<T>) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: identifier })

  const style = {
    transform: CSS.Translate.toString(transform),
    transition,
  }

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={isDragging ? "rounded-sm bg-secondary transition-colors" : ""}
      {...props}
    >
      <li
        key={i}
        className={cn(
          buttonVariants({ variant: "outline" }),
          "grid w-full grid-cols-[92%,1fr]"
        )}
      >
        <div className="flex items-center gap-2">
          <GripVertical
            className="h-6 w-6 p-1"
            {...attributes}
            {...listeners}
          />
          <p className="mr-4 flex items-center gap-2 overflow-hidden text-left">
            {label}
          </p>
        </div>

        <div className="flex gap-4">{actionButtons}</div>
      </li>
    </div>
  )
}
