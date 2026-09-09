"use client"

import * as React from "react"
import * as CheckboxPrimitive from "@radix-ui/react-checkbox"
import { Check } from "lucide-react"

import { cn } from "@/lib/utils"

const Checkbox = React.forwardRef<
  React.ElementRef<typeof CheckboxPrimitive.Root>,
  React.ComponentPropsWithoutRef<typeof CheckboxPrimitive.Root>
>(({ className, ...props }, ref) => (
  <CheckboxPrimitive.Root
    ref={ref}
    className={cn(
      "peer size-4 shrink-0 rounded-sm border border-primary ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 data-[state=checked]:bg-primary data-[state=checked]:text-primary-foreground",
      className
    )}
    {...props}
  >
    <CheckboxPrimitive.Indicator
      className={cn("flex items-center justify-center text-current")}
    >
      <Check className="size-4" />
    </CheckboxPrimitive.Indicator>
  </CheckboxPrimitive.Root>
))
Checkbox.displayName = CheckboxPrimitive.Root.displayName

/**
 * Presentational checkbox for use inside a link or button.
 *
 * Radix's Checkbox renders a <button>, which is invalid inside an <a> or
 * <button> and intercepts clicks meant for the surrounding control. This
 * renders the same visual as an inert <span>, so the enclosing link handles
 * the interaction.
 */
const CheckboxDisplay = React.forwardRef<
  HTMLSpanElement,
  React.ComponentPropsWithoutRef<"span"> & { checked?: boolean }
>(({ className, checked = false, ...props }, ref) => (
  <span
    ref={ref}
    aria-hidden="true"
    data-state={checked ? "checked" : "unchecked"}
    className={cn(
      "peer flex size-4 shrink-0 items-center justify-center rounded-sm border border-primary ring-offset-background data-[state=checked]:bg-primary data-[state=checked]:text-primary-foreground",
      className
    )}
    {...props}
  >
    {checked ? <Check className="size-4" /> : null}
  </span>
))
CheckboxDisplay.displayName = "CheckboxDisplay"

export { Checkbox, CheckboxDisplay }
