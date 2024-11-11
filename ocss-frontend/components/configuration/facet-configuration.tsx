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

import { Facet, Field } from "@/types/config"

import { ConfigSortableItem } from "../demoshop/config-sortable-item"
import CreateFacetButton from "./create-facet-button"
import EditFacetButton from "./edit-facet-button"

type FacetConfigurationProps = {
  onFacetCreation: (newFacet: Facet) => void
  onFacetEdit: (newFacet: Facet, index: number) => void
  onFacetDelete: (facet: Facet, index: number) => void
  facets: Facet[]
  fields: Field[]
  setFacets: (items: Facet[]) => void
}

export function FacetConfiguration({
  onFacetCreation,
  onFacetDelete,
  onFacetEdit,
  facets,
  setFacets,
  fields,
}: FacetConfigurationProps) {
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
      const oldIndex = facets!.findIndex(
        (item) => item["source-field"] == active.id
      )
      const newIndex = facets!.findIndex(
        (item) => item["source-field"] == over.id
      )

      const newItems = arrayMove(facets!, oldIndex, newIndex)
      setFacets(newItems)
    }
  }

  const filteredFields = fields.filter((field) =>
    facets.every((facet) => facet["source-field"] !== field.name)
  )

  return (
    <>
      <h1 className="text-md font-medium">Facets</h1>
      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        onDragEnd={handleDragEnd}
        modifiers={[restrictToVerticalAxis]}
      >
        <SortableContext
          items={facets?.map((facet) => facet["source-field"] ?? "") ?? []}
          strategy={verticalListSortingStrategy}
        >
          <ul className="space-y-2">
            {facets?.map((facet, i) => (
              <ConfigSortableItem<Facet>
                actionButtons={
                  <>
                    <EditFacetButton
                      fields={filteredFields}
                      facet={facet}
                      onSubmit={(newFacet) => onFacetEdit(newFacet, i)}
                    />
                    <Trash2
                      onClick={() => onFacetDelete(facet, i)}
                      className="w-4 cursor-pointer text-red-600"
                    />
                  </>
                }
                label={`${facet["label"]} (${facet["source-field"]})`}
                key={facet["source-field"]}
                identifier={facet["source-field"] ?? ""}
                i={i}
                item={facet}
                onDelete={onFacetDelete}
                onEdit={onFacetEdit}
              />
            ))}
            <CreateFacetButton
              fields={filteredFields}
              facets={facets}
              onSubmit={onFacetCreation}
            />
          </ul>
        </SortableContext>
      </DndContext>
    </>
  )
}
