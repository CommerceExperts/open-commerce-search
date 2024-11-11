export { default } from "next-auth/middleware"

export const config = { matcher: ["/config/:path*"] }
