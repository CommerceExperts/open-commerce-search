"use client"

import { ScoreMode, scoreModes } from "@/types/config"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

type ScoreModeConfigurationProps = {
  scoreMode?: ScoreMode
  onScoreModeChange?: (newScoreMode: ScoreMode) => void
}

export default function ScoreModeConfiguration({
  scoreMode,
  onScoreModeChange,
}: ScoreModeConfigurationProps) {
  return (
    <>
      <h1 className="text-md font-medium">Score mode</h1>
      <Select value={scoreMode ?? "avg"} onValueChange={onScoreModeChange}>
        <SelectTrigger className="w-[180px]">
          <SelectValue placeholder="Select score mode" />
        </SelectTrigger>
        <SelectContent>
          {scoreModes.map((mode) => (
            <SelectItem key={mode} value={mode}>
              {mode}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </>
  )
}
