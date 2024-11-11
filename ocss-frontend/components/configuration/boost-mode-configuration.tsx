"use client"

import { BoostMode, boostModes } from "@/types/config"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

type BoostModeConfigurationProps = {
  boostMode?: BoostMode
  onBoostModeChange?: (newBoostMode: BoostMode) => void
}

export default function BoostModeConfiguration({
  boostMode,
  onBoostModeChange,
}: BoostModeConfigurationProps) {
  return (
    <>
      <h1 className="text-md font-medium">Boost mode</h1>
      <Select value={boostMode ?? "avg"} onValueChange={onBoostModeChange}>
        <SelectTrigger className="w-[180px]">
          <SelectValue placeholder="Select boost mode" />
        </SelectTrigger>
        <SelectContent>
          {boostModes.map((mode) => (
            <SelectItem key={mode} value={mode}>
              {mode}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </>
  )
}
