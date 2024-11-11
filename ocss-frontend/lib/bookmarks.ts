"use server"

import { cookies } from "next/headers"

import { Bookmark } from "@/types/bookmarks"
import { InternalCookies } from "@/types/cookies"

export async function setBookmarksCookie(items: Bookmark[]) {
  cookies().set(InternalCookies.bookmarks, JSON.stringify(items))
}

export async function getBookmarksFromCookie() {
  return JSON.parse(
    cookies().get(InternalCookies.bookmarks)?.value ?? "[]"
  ) as Bookmark[]
}
