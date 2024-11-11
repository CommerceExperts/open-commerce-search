"use client"

import { useEffect, useState, useTransition } from "react"
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
import { Save, Trash2 } from "lucide-react"
import { useRecoilState } from "recoil"

import {
  ProductDataFieldConfiguration,
  ProductDataFieldConfigurationCurrency,
  productDataFieldConfigurationCurrencies,
  productDataFieldConfigurationStyles,
  productDataFieldConfigurationTypes,
} from "@/types/config"
import { productDataFieldConfigurationState } from "@/lib/global-state"
import { setProductDataFieldConfigurationCookie } from "@/lib/product-card-configuration"
import { cn } from "@/lib/utils"
import { Card, CardContent } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

import { Button, buttonVariants } from "../ui/button"
import { Input } from "../ui/input"
import { SortableItem } from "./sortable-item"

type ProductCardEditorProps = {
  productDataFields: string[]
  className?: string
  children?: React.ReactNode
}

export default function ProductCardEditorButton({
  productDataFields,
  className,
  children,
}: ProductCardEditorProps) {
  const [dialogOpen, setDialogOpen] = useState(false)
  const [addDataFieldSelectValue, setAddDataFieldSelectValue] = useState("")
  const [items, setItems] = useState<ProductDataFieldConfiguration[]>([])
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
  const [selectedItem, setSelectedItem] = useState<number | null>(null)
  const [productDataFieldConfiguration, setProductDataFieldConfiguration] =
    useRecoilState(productDataFieldConfigurationState)
  const [isPending, startTransition] = useTransition()

  function handleDragEnd(event: any) {
    setSelectedItem(null)

    const { active, over } = event

    if (active.id !== over.id) {
      setItems((items) => {
        const oldIndex = items.findIndex(
          (item) => item.sourceField == active.id
        )
        const newIndex = items.findIndex((item) => item.sourceField == over.id)

        return arrayMove(items, oldIndex, newIndex)
      })
    }
  }

  useEffect(() => {
    if (dialogOpen) {
      setItems(productDataFieldConfiguration)
      setSelectedItem(null)
    }
  }, [dialogOpen])

  return (
    <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
      <DialogTrigger className={cn(buttonVariants(), className)}>
        {children}
      </DialogTrigger>
      <DialogContent className="max-h-screen overflow-y-scroll lg:max-w-screen-sm">
        <DialogHeader>
          <DialogTitle>Product card editor</DialogTitle>
          <DialogDescription>
            Select data fields, choose data field types and styles for the
            product cards in the search results. Change order by drag and drop.
          </DialogDescription>
          <div className="mx-auto w-[300px] py-4">
            <Card>
              <CardContent>
                <DndContext
                  sensors={sensors}
                  collisionDetection={closestCenter}
                  onDragEnd={handleDragEnd}
                  modifiers={[restrictToVerticalAxis]}
                >
                  <SortableContext
                    items={items.map((item) => item.sourceField)}
                    strategy={verticalListSortingStrategy}
                  >
                    <ul className="my-4 space-y-2">
                      {items.map((item, i) => (
                        <SortableItem
                          key={item.sourceField}
                          identifier={item.sourceField}
                          onClick={() => {
                            setSelectedItem(i)
                          }}
                        >
                          {item.type === "image" ? (
                            <div className="flex h-52 w-full items-center justify-center rounded-md border">
                              <p>{item.sourceField}</p>
                            </div>
                          ) : (
                            <p
                              className={cn(
                                "truncate rounded-md border px-2 py-1",
                                item.style == "bold"
                                  ? "font-bold"
                                  : item.style === "small"
                                  ? "text-sm"
                                  : ""
                              )}
                            >
                              {item.sourceField}
                            </p>
                          )}
                        </SortableItem>
                      ))}
                    </ul>
                  </SortableContext>
                </DndContext>

                <Select
                  value={addDataFieldSelectValue}
                  onValueChange={(value) => {
                    setItems((items) =>
                      items.concat({
                        type: "string",
                        sourceField: value,
                        style: "regular",
                      })
                    )
                    setSelectedItem(items.length)
                  }}
                >
                  <SelectTrigger
                    className={cn(
                      buttonVariants(),
                      "flex h-9 w-full gap-2 rounded-full font-bold"
                    )}
                  >
                    Add data field
                  </SelectTrigger>
                  <SelectContent>
                    {productDataFields
                      .filter(
                        (productDataField) =>
                          !items.some(
                            (item) => item.sourceField === productDataField
                          )
                      )
                      .map((productDataField) => (
                        <SelectItem
                          key={productDataField}
                          value={productDataField}
                        >
                          {productDataField}
                        </SelectItem>
                      ))}
                  </SelectContent>
                </Select>
              </CardContent>
            </Card>
          </div>
          {selectedItem !== null && (
            <div className="space-y-2 py-4">
              <h2 className="text-md font-bold">
                Edit field &quot;{items[selectedItem].sourceField}&quot;
              </h2>
              <div className="flex flex-wrap gap-2">
                <Select
                  onValueChange={(value: "string" | "price" | "image") =>
                    setItems((items) =>
                      items.map((item) =>
                        item.sourceField === items[selectedItem].sourceField
                          ? { ...item, type: value }
                          : item
                      )
                    )
                  }
                  value={items[selectedItem].type}
                >
                  <SelectTrigger className="w-[180px]">
                    <SelectValue placeholder="Type" />
                  </SelectTrigger>
                  <SelectContent>
                    {productDataFieldConfigurationTypes.map(
                      (productDataFieldConfigurationType) => (
                        <SelectItem
                          key={productDataFieldConfigurationType}
                          value={productDataFieldConfigurationType}
                        >
                          {productDataFieldConfigurationType}
                        </SelectItem>
                      )
                    )}
                  </SelectContent>
                </Select>

                {items[selectedItem].type !== "image" && (
                  <Select
                    onValueChange={(value: "regular" | "bold" | "small") =>
                      setItems((items) =>
                        items.map((item) =>
                          item.sourceField === items[selectedItem].sourceField
                            ? { ...item, style: value }
                            : item
                        )
                      )
                    }
                    value={items[selectedItem].style}
                  >
                    <SelectTrigger className="w-[180px]">
                      <SelectValue placeholder="Style" />
                    </SelectTrigger>
                    <SelectContent>
                      {productDataFieldConfigurationStyles.map(
                        (productDataFieldConfigurationStyle) => (
                          <SelectItem
                            key={productDataFieldConfigurationStyle}
                            value={productDataFieldConfigurationStyle}
                          >
                            {productDataFieldConfigurationStyle}
                          </SelectItem>
                        )
                      )}
                    </SelectContent>
                  </Select>
                )}

                {items[selectedItem].type === "price" && (
                  <>
                    <Select
                      onValueChange={(
                        value: ProductDataFieldConfigurationCurrency
                      ) =>
                        setItems((items) =>
                          items.map((item) =>
                            item.sourceField === items[selectedItem].sourceField
                              ? { ...item, currency: value }
                              : item
                          )
                        )
                      }
                      value={items[selectedItem].currency}
                    >
                      <SelectTrigger className="w-[140px]">
                        <SelectValue placeholder="Currency" />
                      </SelectTrigger>
                      <SelectContent>
                        {productDataFieldConfigurationCurrencies.map(
                          (productDataFieldConfigurationCurrency) => (
                            <SelectItem
                              key={productDataFieldConfigurationCurrency}
                              value={productDataFieldConfigurationCurrency}
                            >
                              {productDataFieldConfigurationCurrency}
                            </SelectItem>
                          )
                        )}
                      </SelectContent>
                    </Select>

                    <Input
                      type="number"
                      className="w-38"
                      placeholder="Divisor (default is 1.0)"
                      value={items[selectedItem].divisor}
                      onChange={(e) => {
                        setItems((items) =>
                          items.map((item) =>
                            item.sourceField === items[selectedItem].sourceField
                              ? {
                                  ...item,
                                  divisor: parseFloat(e.target.value),
                                }
                              : item
                          )
                        )
                      }}
                    />
                  </>
                )}

                <Button
                  onClick={() => {
                    setItems((items) =>
                      items.filter(
                        (item) =>
                          item.sourceField !== items[selectedItem].sourceField
                      )
                    )
                    setSelectedItem(null)
                  }}
                  variant="destructive"
                  className="flex items-center gap-2 font-bold"
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              </div>
            </div>
          )}
          <Button
            onClick={() => {
              setProductDataFieldConfiguration(items)
              setDialogOpen(false)
              startTransition(() => {
                setProductDataFieldConfigurationCookie(items)
              })
            }}
            className="flex max-w-[200px] gap-2 font-bold"
          >
            Save configuration <Save className="h-4 w-4" />
          </Button>
        </DialogHeader>
      </DialogContent>
    </Dialog>
  )
}
