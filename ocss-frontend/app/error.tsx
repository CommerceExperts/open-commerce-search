"use client"

export default function ErrorPage() {
  return (
    <section className="mt-12 flex flex-col items-center justify-center">
      <p className="mb-4 text-9xl font-black">Oops!</p>
      <h1 className="max-w-2xl text-center text-2xl font-semibold">
        Something very bad happened. We apologize for any inconveniences caused
        by this system error.
      </h1>
    </section>
  )
}
