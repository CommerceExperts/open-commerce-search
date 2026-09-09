import { useEffect, useRef, useState } from "react"

/**
 * Returns a copy of `value` that only updates after `wait` milliseconds
 * without a change. Drop-in replacement for Mantine's `useDebouncedValue`.
 */
export function useDebouncedValue<T>(value: T, wait: number) {
  const [debouncedValue, setDebouncedValue] = useState(value)
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const cancel = () => {
    if (timeoutRef.current !== null) {
      clearTimeout(timeoutRef.current)
      timeoutRef.current = null
    }
  }

  useEffect(() => {
    cancel()
    timeoutRef.current = setTimeout(() => setDebouncedValue(value), wait)
    return cancel
  }, [value, wait])

  return [debouncedValue, cancel] as const
}
