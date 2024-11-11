"use client"

import { Input } from "../ui/input"

type MaxFacetsConfigurationProps = {
  onValueChange: (value: string) => void
  value: number | undefined
}

export default function MaxFacetsConfiguration({
  onValueChange,
  value,
}: MaxFacetsConfigurationProps) {
  return (
    <>
      <h1 className="text-md font-medium">Max facets</h1>
      <Input
        value={value ?? ""}
        onChange={(e) => {
          onValueChange(e.target.value)
        }}
        placeholder="Enter max facets"
        type="number"
      />
    </>
  )
}
