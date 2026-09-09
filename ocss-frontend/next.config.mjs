import "./env.mjs"

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  output: "standalone",
  basePath: process.env.BASEPATH,
  images: {
    unoptimized: true,
  },
}

export default nextConfig
