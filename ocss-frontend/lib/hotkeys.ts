import type { KeyboardEvent as ReactKeyboardEvent } from "react"

type HotkeyHandler = (event: KeyboardEvent) => void
type HotkeyItem = [string, HotkeyHandler, { preventDefault?: boolean }?]

const MODIFIER_KEYS = ["alt", "ctrl", "meta", "shift", "mod"]

type ParsedHotkey = {
  alt: boolean
  ctrl: boolean
  meta: boolean
  mod: boolean
  shift: boolean
  key?: string
}

function parseHotkey(hotkey: string): ParsedHotkey {
  const keys = hotkey
    .toLowerCase()
    .split("+")
    .map((part) => part.trim())

  return {
    alt: keys.includes("alt"),
    ctrl: keys.includes("ctrl"),
    meta: keys.includes("meta"),
    mod: keys.includes("mod"),
    shift: keys.includes("shift"),
    key: keys.find((key) => !MODIFIER_KEYS.includes(key)),
  }
}

function isExactHotkey(hotkey: ParsedHotkey, event: KeyboardEvent): boolean {
  const { alt, ctrl, meta, mod, shift, key } = hotkey

  if (alt !== event.altKey) return false
  if (mod) {
    if (!event.ctrlKey && !event.metaKey) return false
  } else {
    if (ctrl !== event.ctrlKey) return false
    if (meta !== event.metaKey) return false
  }
  if (shift !== event.shiftKey) return false

  if (!key) return false

  return (
    event.key.toLowerCase() === key ||
    event.code.replace("Key", "").toLowerCase() === key
  )
}

/**
 * Builds a `onKeyDown` handler from `[hotkey, handler]` pairs.
 * Drop-in replacement for Mantine's `getHotkeyHandler`.
 */
export function getHotkeyHandler(hotkeys: HotkeyItem[]) {
  return (event: ReactKeyboardEvent<HTMLElement> | KeyboardEvent) => {
    const nativeEvent =
      "nativeEvent" in event ? (event.nativeEvent as KeyboardEvent) : event

    hotkeys.forEach(([hotkey, handler, options = { preventDefault: true }]) => {
      if (isExactHotkey(parseHotkey(hotkey), nativeEvent)) {
        if (options.preventDefault !== false) {
          event.preventDefault()
        }
        handler(nativeEvent)
      }
    })
  }
}
