import React from "react"
import { UniqueIdentifier } from "@dnd-kit/core"
import { useSortable } from "@dnd-kit/sortable"
import { CSS } from "@dnd-kit/utilities"

type SortableItemProps = {
  identifier: UniqueIdentifier
} & React.ComponentProps<"div">

export function SortableItem({ identifier, ...props }: SortableItemProps) {
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
      {...attributes}
      {...listeners}
      {...props}
    >
      {props.children}
    </div>
  )
}
