"use client"

import { Card, CardHeader } from "@/components/ui/card"

import { Switch } from "./switch"

type UseDefaultConfigSwitchProps = {
  onCheckedChange: (useDefaultConfig: boolean) => void
  checked: boolean
}

export default function UseDefaultConfigSwitch({
  onCheckedChange,
  checked,
}: UseDefaultConfigSwitchProps) {
  return (
    <div className="flex gap-4">
      <Card className="w-full">
        <CardHeader className="flex flex-row items-center justify-between py-4">
          <p className="font-semibold">Use default config</p>
          <Switch checked={checked} onCheckedChange={onCheckedChange} />
        </CardHeader>
      </Card>
    </div>
  )
}
