import { createFileRoute } from "@tanstack/react-router";
import { SiteHeader } from "@/components/navigation/site-header";

export const Route = createFileRoute("/data")({
  component: App,
});

function App() {
  return (
    <>
      <SiteHeader>
        <h1 className="text-base font-medium">Data</h1>
      </SiteHeader>
    </>
  );
}
