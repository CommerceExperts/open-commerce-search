"use client"

import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
} from "@dnd-kit/core"
import { restrictToVerticalAxis } from "@dnd-kit/modifiers"
import {
  SortableContext,
  arrayMove,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable"
import { Trash2 } from "lucide-react"

import { QueryProcessingConfigurationItem } from "@/types/config"

import { ConfigSortableItem } from "../demoshop/config-sortable-item"
import CreateQueryProcessingConfigurationButton from "./create-query-processing-configuration-button"
import EditQueryProcessingConfigurationButton from "./edit-query-processing-configuration-button"

type QueryProcessingConfigurationProps = {
  onCreate: (item: QueryProcessingConfigurationItem) => void
  onEdit: (item: QueryProcessingConfigurationItem, index: number) => void
  onDelete: (item: QueryProcessingConfigurationItem, index: number) => void
  items?: QueryProcessingConfigurationItem[]
  setItems: (items: QueryProcessingConfigurationItem[]) => void
}

export function QueryProcessingConfiguration({
  onCreate,
  onDelete,
  onEdit,
  items,
  setItems,
}: QueryProcessingConfigurationProps) {
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 10,
      },
    }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    })
  )

  function handleDragEnd(event: any) {
    const { active, over } = event

    if (active.id !== over.id) {
      const oldIndex = items!.findIndex((item) => item.type == active.id)
      const newIndex = items!.findIndex((item) => item.type == over.id)

      const newItems = arrayMove(items!, oldIndex, newIndex)
      setItems(newItems)
    }
  }

  return (
    <>
      <h1 className="text-md font-medium">Query Preprocessors</h1>
      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        onDragEnd={handleDragEnd}
        modifiers={[restrictToVerticalAxis]}
      >
        <SortableContext
          items={items?.map((item) => item.type) ?? []}
          strategy={verticalListSortingStrategy}
        >
          <ul className="space-y-2">
            {items?.map((item, i) => (
              <ConfigSortableItem<QueryProcessingConfigurationItem>
                actionButtons={
                  <>
                    <EditQueryProcessingConfigurationButton
                      item={item}
                      onSubmit={(_queryProcessingConfiguration) =>
                        onEdit(_queryProcessingConfiguration, i)
                      }
                    />
                    <Trash2
                      onClick={() => onDelete(item, i)}
                      className="w-4 cursor-pointer text-red-600"
                    />
                  </>
                }
                label={item.type}
                key={item.type}
                identifier={item.type}
                i={i}
                item={item}
                onDelete={onDelete}
                onEdit={onEdit}
              />
            ))}
            <CreateQueryProcessingConfigurationButton
              items={items}
              onSubmit={onCreate}
            />
          </ul>
        </SortableContext>
      </DndContext>
    </>
  )
}
