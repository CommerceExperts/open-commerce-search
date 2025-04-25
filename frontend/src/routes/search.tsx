import { createFileRoute } from "@tanstack/react-router";
import { SiteHeader } from "@/components/navigation/site-header";
import { TenantSelect } from "@/components/configuration/tenant-select";
import { SettingsButton } from "@/components/search/settings-button";
import { DetailsButton } from "@/components/search/details-button";
import { SearchInput } from "@/components/search/search-input";
import { ResultCard } from "@/components/search/result-card";
import { SortSelect } from "@/components/search/sort-select";
import { FilterButton } from "@/components/search/filter-button";

export const Route = createFileRoute("/search")({
  component: App,
});

function App() {
  return (
    <>
      <SiteHeader>
        <div className="flex justify-between items-center w-full">
          <h1 className="text-base font-medium">Search</h1>
          <SearchInput className="lg:fixed lg:left-1/2 lg:-translate-x-1/2" />
          <div className="flex gap-2 items-center">
            <TenantSelect />
            <SettingsButton />
            <DetailsButton />
          </div>
        </div>
      </SiteHeader>
      <div className="flex flex-1 flex-col gap-4 p-4">
        <div className="flex items-center justify-between">
          <h3>
            <span className="font-bold">All results</span> (1)
          </h3>
          <div className="flex items-center gap-2">
            <div className="flex items-center gap-2">
              <p className="text-sm font-medium leading-none">Sort by</p>
              <SortSelect />
            </div>
            <FilterButton />
          </div>
        </div>
        <ResultCard />
      </div>
    </>
  );
}
