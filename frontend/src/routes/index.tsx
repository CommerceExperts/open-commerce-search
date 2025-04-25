import { createFileRoute } from "@tanstack/react-router";
import { SiteHeader } from "@/components/navigation/site-header";
import {
  ElasticsearchStatusCard,
  IndexListCard,
  IndexServiceStatusCard,
  SearchServiceStatusCard,
  WelcomeMessageCard,
} from "@/components/home/cards";

export const Route = createFileRoute("/")({
  component: App,
});

function App() {
  return (
    <>
      <SiteHeader>
        <h1 className="text-base font-medium">Home</h1>
      </SiteHeader>
      <div className="grid grid-cols-4 grid-rows-3 gap-4 p-4">
        <WelcomeMessageCard className="col-span-4 xl:col-span-3" />
        <IndexListCard className="col-span-4 xl:col-span-1 row-span-2" />
        <SearchServiceStatusCard className="col-span-4 xl:col-span-1 row-span-1" />
        <IndexServiceStatusCard className="col-span-4 xl:col-span-1 row-span-1 " />
        <ElasticsearchStatusCard className="col-span-4 xl:col-span-1 row-span-1" />
      </div>
    </>
  );
}
