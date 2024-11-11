import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

import {
  SearchParam,
  SearchParams,
  SearchParamsMap,
} from "@/types/searchParams"

import { defaultSortOption } from "./search-api"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function range(from: number, to: number) {
  return Array.from({ length: to - from + 1 }, (_, i) => i + from)
}

export function deleteURLSearchParams(
  searchParams: URLSearchParams,
  deleted: string[]
) {
  const params = new URLSearchParams(searchParams)

  for (const keyDeleted of deleted) {
    searchParams.forEach((_, key) => {
      if (key == keyDeleted) {
        params.delete(key)
      }
    })
  }

  return params
}

export function deleteURLSearchParamsWithValue(
  searchParams: URLSearchParams,
  deleted: string[],
  value: string
) {
  const params = new URLSearchParams(searchParams)

  for (const key of deleted) {
    for (const _value of searchParams.getAll(key)) {
      if (_value == value) {
        params.delete(key)
      }
    }
  }

  return params
}

export function removeQueryParamValue(
  searchParams: URLSearchParams,
  paramName: string,
  valueToRemove: string
) {
  if (searchParams.has(paramName)) {
    const currentValues = searchParams.getAll(paramName)

    const updatedValues = currentValues
      .map((value) =>
        value
          .split(",")
          .filter((v) => v !== valueToRemove)
          .join(",")
      )
      .filter((value) => value !== "")

    if (updatedValues.length > 0) {
      searchParams.set(paramName, updatedValues.join(","))
    } else {
      searchParams.delete(paramName)
    }
  }

  return searchParams
}

export function includeURLSearchParams(
  searchParams: URLSearchParams,
  included: string[]
) {
  const params = new URLSearchParams(searchParams)

  searchParams.forEach((_, key) => {
    const isIncluded = included.find((include) => key === include)

    if (!isIncluded) {
      params.delete(key)
    }
  })

  return params
}

export function mergeURLSearchParams(
  searchParams1: URLSearchParams,
  searchParams2: URLSearchParams
) {
  const mergedURLSearchParams = searchParams1

  searchParams2.forEach((value, key) => {
    mergedURLSearchParams.append(key, value)
  })

  return mergedURLSearchParams
}

export function extractSearchParam(
  tenantSearchParam: SearchParam,
  index: number
) {
  if (tenantSearchParam) {
    if (Array.isArray(tenantSearchParam)) {
      return tenantSearchParam[index]
    } else {
      return tenantSearchParam
    }
  } else {
    return ""
  }
}

export function extractPage(pageParam: SearchParam) {
  const _page = parseInt(extractSearchParam(pageParam, 0))
  const page = !isNaN(_page) ? _page : 1

  return page
}

export function extractSort(sortParam: SearchParam) {
  const sort = extractSearchParam(sortParam, 0) || defaultSortOption.field

  return sort
}

export function extractFilters(searchParams: SearchParams) {
  const filters = Object.keys(searchParams).reduce((obj, key) => {
    for (const searchParam in SearchParamsMap) {
      if (key === searchParam) {
        return obj
      }
    }

    if (Array.isArray(searchParams[key])) {
      obj[key] = (searchParams[key] as string[]).map((item) =>
        (item as string).replace(/,/g, "%2C")
      )
    } else {
      obj[key] = (searchParams[key] as string).replace(/,/g, "%2C")
    }

    return obj
  }, {} as { [key: string]: string | string[] | undefined })

  return filters
}

export function extractHeroProductIds(searchParam: SearchParam) {
  if (searchParam) {
    if (Array.isArray(searchParam)) {
      return searchParam.flatMap((item) => item.split(","))
    } else {
      return searchParam.split(",")
    }
  } else {
    return []
  }
}

export function decodeBase64(base64String: string) {
  const binaryString = atob(base64String)
  const uint8Array = new Uint8Array(binaryString.length)

  for (let i = 0; i < binaryString.length; i++) {
    uint8Array[i] = binaryString.charCodeAt(i)
  }

  const textDecoder = new TextDecoder()
  const utf8String = textDecoder.decode(uint8Array)

  return utf8String
}

export function encodeBase64(utf8String: string) {
  const textEncoder = new TextEncoder()
  const uint8Array = textEncoder.encode(utf8String)

  let binaryString = ""
  uint8Array.forEach((byte) => {
    binaryString += String.fromCharCode(byte)
  })

  const base64String = btoa(binaryString)
  return base64String
}

export const urlPattern = /^(https?:\/\/|\/)/

export function moveItemUp(array: any[], currentIndex: number) {
  const newIndex = currentIndex - 1
  if (newIndex >= 0 && newIndex < array.length) {
    const temp = array[currentIndex]
    array[currentIndex] = array[newIndex]
    array[newIndex] = temp
  }
  return array
}

export function moveItemDown(array: any[], currentIndex: number) {
  const newIndex = currentIndex + 1
  if (newIndex < array.length) {
    const temp = array[currentIndex]
    array[currentIndex] = array[newIndex]
    array[newIndex] = temp
  }
  return array
}
