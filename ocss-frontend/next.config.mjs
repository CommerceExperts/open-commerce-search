import "./env.mjs"

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  output: "standalone",
  basePath: process.env.BASEPATH,
  images: {
    // remotePatterns: [
    //   {
    //     protocol: "https",
    //     hostname: "**",
    //   },
    // ],
    unoptimized: true,
  },
}

export default nextConfig
