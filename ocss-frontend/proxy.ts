import type { NextFetchEvent } from "next/server"
import withAuth, { type NextRequestWithAuth } from "next-auth/middleware"

// Next.js 16 renamed the `middleware` convention to `proxy` and requires an
// explicit function export (a bare `export { default } from ...` re-export is
// not detected). `withAuth` acts as the handler when called with (req, event).
export function proxy(request: NextRequestWithAuth, event: NextFetchEvent) {
  return withAuth(request, event)
}

export const config = { matcher: ["/config/:path*"] }
