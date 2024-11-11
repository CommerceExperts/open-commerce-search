"use client"

import { Trash2 } from "lucide-react"

import { Field, ScoreFunction } from "@/types/config"
import { cn } from "@/lib/utils"

import { buttonVariants } from "../ui/button"
import CreateScoreFunctionButton from "./create-score-function-button"
import EditScoreFunctionButton from "./edit-score-function-button"

type ScoringConfigurationProps = {
  onScoreFunctionCreation: (newScoreFunction: ScoreFunction) => void
  onScoreFunctionEdit: (newScoreFunction: ScoreFunction, index: number) => void
  onScoreFunctionDelete: (
    newScoreFunction: ScoreFunction,
    index: number
  ) => void
  scoreFunctions?: ScoreFunction[]
  fields: Field[]
}

export function ScoringConfiguration({
  onScoreFunctionCreation,
  onScoreFunctionDelete,
  onScoreFunctionEdit,
  scoreFunctions,
  fields,
}: ScoringConfigurationProps) {
  return (
    <>
      <h1 className="text-md font-medium">Score functions</h1>
      <ul className="space-y-2">
        {scoreFunctions?.map((scoreFunction, i) => (
          <li
            key={i}
            className={cn(
              buttonVariants({ variant: "outline" }),
              "w-full justify-between"
            )}
          >
            <p className="flex items-center gap-2 text-left">
              {scoreFunction.type}{" "}
            </p>
            <div className="flex gap-4">
              <EditScoreFunctionButton
                scoreFunction={scoreFunction}
                onSubmit={(_scoreFunction) =>
                  onScoreFunctionEdit(_scoreFunction, i)
                }
                fields={fields}
              />
              <Trash2
                onClick={() => onScoreFunctionDelete(scoreFunction, i)}
                className="w-4 cursor-pointer text-red-600"
              />
            </div>
          </li>
        ))}
        <CreateScoreFunctionButton
          onSubmit={onScoreFunctionCreation}
          fields={fields}
        />
      </ul>
    </>
  )
}
