"use client"

import { useEffect, useRef, useState } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { getHotkeyHandler, useDebouncedValue } from "@mantine/hooks"

import { Suggestion } from "@/types/api"
import { SearchParamsMap } from "@/types/searchParams"
import { suggest } from "@/lib/suggest-api"
import { cn } from "@/lib/utils"

import { Icons } from "../misc/icons"
import { buttonVariants } from "../ui/button"

type SearchbarProps = {
  defaultValue?: string
  tenant: string
  basepath: string
  enableSuggest: boolean
}

export default function Searchbar({
  defaultValue = "",
  tenant,
  basepath,
  enableSuggest,
}: SearchbarProps) {
  const router = useRouter()

  const [searchInput, setSearchInput] = useState(defaultValue)
  const [deferredSearchInput] = useDebouncedValue(searchInput, 300)
  const inputRef = useRef<HTMLInputElement>(null)

  const [suggestions, setSuggestions] = useState<Suggestion[]>([])
  const [showSuggestions, setShowSuggestions] = useState<boolean>(false)
  const [suggestionBarHover, setSuggestionBarHover] = useState<boolean>(false)

  const [suggestionFocused, setSuggestionFocused] = useState(-1)
  const suggestionListRef = useRef<HTMLUListElement>(null)

  const focusSuggestionAbove = () => {
    if (
      suggestionListRef.current &&
      suggestionListRef.current.children[suggestionFocused - 1]
    ) {
      ;(
        suggestionListRef.current.children[
          suggestionFocused - 1
        ] as HTMLLinkElement
      ).focus()
      setSuggestionFocused((suggestionFocused) => suggestionFocused - 1)
    }
  }

  const focusSuggestionBelow = () => {
    if (
      suggestionListRef.current &&
      suggestionListRef.current.children[suggestionFocused + 1]
    ) {
      ;(
        suggestionListRef.current.children[
          suggestionFocused + 1
        ] as HTMLLinkElement
      ).focus()
      setSuggestionFocused((suggestionFocused) => suggestionFocused + 1)
    }
  }

  const focusInput = () => {
    inputRef.current?.focus()
    setSuggestionFocused(-1)
  }

  const search = () => {
    setShowSuggestions(false)

    router.push(
      "/" +
        "?" +
        new URLSearchParams([
          [SearchParamsMap.query, searchInput],
          [SearchParamsMap.tenant, tenant],
        ]).toString()
    )
  }

  useEffect(() => {
    if (enableSuggest) {
      setSuggestionFocused(-1)

      if (deferredSearchInput.trim().length < 1) {
        setSuggestions([])
      } else {
        ;(async () => {
          const suggestions = await suggest(
            tenant,
            deferredSearchInput,
            10,
            basepath
          )
          setSuggestions(suggestions)
        })()
      }
    }
  }, [deferredSearchInput, tenant, basepath])

  useEffect(() => {
    if (!showSuggestions) {
      setSuggestionFocused(-1)
    }
  }, [showSuggestions])

  return (
    <>
      <div className="h-14 w-full bg-accent" />

      <label className="relative left-1/2 block w-[90%] max-w-[500px]  translate-x-[-50%] translate-y-[-50%] text-gray-400 drop-shadow-xl focus-within:text-gray-600">
        <Icons.search
          data-track-id="searchButton"
          onClick={(e) => {
            e.stopPropagation()
            search()
          }}
          className="absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 cursor-pointer"
        />

        <form
          onSubmit={(e) => {
            e.preventDefault()
            search()
          }}
        >
          <input
            data-track-id="searchBox"
            ref={inputRef}
            onKeyDown={getHotkeyHandler([
              ["ArrowUp", focusSuggestionAbove],
              ["ArrowDown", focusSuggestionBelow],
              ["Tab+Shift", focusSuggestionAbove],
              ["Tab", focusSuggestionBelow],
            ])}
            onFocus={() => setShowSuggestions(true)}
            onBlur={() => {
              if (!suggestionBarHover) {
                setShowSuggestions(false)
              }
            }}
            type="text"
            name="searchinput"
            id="searchinput"
            placeholder="Enter search"
            onChange={(e) => setSearchInput(e.target.value)}
            value={searchInput}
            className={`${
              showSuggestions && suggestions.length > 0
                ? "rounded-t-md"
                : "rounded-md"
            } form-input z-50 block h-10 w-full appearance-none border border-input bg-popover px-4 py-3 pl-14 text-sm text-primary ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-gray-400 focus-visible:outline-none  focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50`}
          />
          {showSuggestions && (
            <ul
              onKeyDown={getHotkeyHandler([["Escape", focusInput]])}
              ref={suggestionListRef}
              onMouseOver={() => setSuggestionBarHover(true)}
              onMouseLeave={() => setSuggestionBarHover(false)}
              className="absolute w-full rounded-b-md border border-input bg-background"
            >
              {suggestions.map((suggestion) => (
                <Link
                  data-track-id="suggestSearchTerm"
                  onKeyDown={getHotkeyHandler([
                    ["ArrowUp", focusSuggestionAbove],
                    ["ArrowDown", focusSuggestionBelow],
                    ["Tab+Shift", focusSuggestionAbove],
                    ["Tab", focusSuggestionBelow],
                  ])}
                  onFocus={() => setShowSuggestions(true)}
                  onBlur={() => {
                    if (!suggestionBarHover) {
                      setShowSuggestions(false)
                    }
                  }}
                  href={
                    "/?" +
                    new URLSearchParams([
                      [SearchParamsMap.query, suggestion.phrase ?? ""],
                      [SearchParamsMap.tenant, tenant],
                    ]).toString()
                  }
                  onClick={() => {
                    setShowSuggestions(false)
                    setSearchInput(suggestion.phrase ?? "")
                  }}
                  className={cn(
                    buttonVariants({ variant: "ghost" }),
                    "block h-8 w-full truncate rounded-none"
                  )}
                  key={suggestion.phrase}
                >
                  {suggestion.phrase}
                </Link>
              ))}
            </ul>
          )}
        </form>
      </label>
    </>
  )
}
